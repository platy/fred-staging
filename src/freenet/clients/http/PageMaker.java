package freenet.clients.http;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import freenet.l10n.L10n;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.FredPluginL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;

/** Simple class to output standard heads and tail for web interface pages. 
*/
public final class PageMaker {
	
	public enum THEME {
		BOXED("boxed", "Boxed", ""),
		CLEAN("clean", "Clean", "Mr. Proper"),
		CLEAN_DROPDOWN("clean-dropdown", "Clean (Dropdown menu)", "Clean theme with a dropdown menu."),
		CLEAN_STATIC("clean-static", "Clean (Static menu)", "Clean theme with a static menu."),
		GRAYANDBLUE("grayandblue", "Gray And Blue", ""),
		SKY("sky", "Sky", ""),
                MINIMALBLUE("minimalblue", "Minimal Blue", "A minimalistic theme in blue");
		
		public static final String[] possibleValues = {
			BOXED.code,
			CLEAN.code,
			CLEAN_DROPDOWN.code,
			CLEAN_STATIC.code,
			GRAYANDBLUE.code,
			SKY.code,
                        MINIMALBLUE.code
		};
		
		public final String code;  // the internal name
		public final String name;  // the name in "human form"
		public final String description; // description
		
		private THEME(String code, String name, String description) {
			this.code = code;
			this.name = name;
			this.description = description;
		}

		public static THEME themeFromName(String cssName) {
			for(THEME t : THEME.values()) {
				if(t.code.equalsIgnoreCase(cssName) ||
				   t.name.equalsIgnoreCase(cssName))
				{
					return t;
				}
			}
			return getDefault();
		}

		public static THEME getDefault() {
			return THEME.CLEAN;
		}
	}	
	
	public static final int MODE_SIMPLE = 1;
	public static final int MODE_ADVANCED = 2;
	private THEME theme;
	private File override;
	
	private List<SubMenu> menuList = new ArrayList<SubMenu>();
	private Map<String, SubMenu> subMenus = new HashMap<String, SubMenu>();
	
	private class SubMenu {
		
		/** Name of the submenu */
		private final String navigationLinkText;
		/** Link if the user clicks on the submenu itself */
		private final String defaultNavigationLink;
		/** Tooltip */
		private final String defaultNavigationLinkTitle;
		
		private final FredPluginL10n plugin;
		
		private final List<String> navigationLinkTexts = new ArrayList<String>();
		private final List<String> navigationLinkTextsNonFull = new ArrayList<String>();
		private final Map<String, String> navigationLinkTitles = new HashMap<String, String>();
		private final Map<String, String> navigationLinks = new HashMap<String, String>();
		private final Map<String, LinkEnabledCallback>  navigationLinkCallbacks = new HashMap<String, LinkEnabledCallback>();
		
		public SubMenu(String link, String name, String title, FredPluginL10n plugin) {
			this.navigationLinkText = name;
			this.defaultNavigationLink = link;
			this.defaultNavigationLinkTitle = title;
			this.plugin = plugin;
		}

		public void addNavigationLink(String path, String name, String title, boolean fullOnly, LinkEnabledCallback cb) {
			navigationLinkTexts.add(name);
			if(!fullOnly)
				navigationLinkTextsNonFull.add(name);
			navigationLinkTitles.put(name, title);
			navigationLinks.put(name, path);
			if(cb != null)
				navigationLinkCallbacks.put(name, cb);
		}

		@Deprecated
		public void removeNavigationLink(String name) {
			navigationLinkTexts.remove(name);
			navigationLinkTextsNonFull.remove(name);
			navigationLinkTitles.remove(name);
			navigationLinks.remove(name);
		}

		@Deprecated
		public void removeAllNavigationLinks() {
			navigationLinkTexts.clear();
			navigationLinkTextsNonFull.clear();
			navigationLinkTitles.clear();
			navigationLinks.clear();
		}
	}
	
	protected PageMaker(THEME t) {
		setTheme(t);
	}
	
	void setOverride(File f) {
		this.override = f;
	}
	
	public void setTheme(THEME theme2) {
		if (theme2 == null) {
			this.theme = THEME.getDefault();
		} else {
			URL themeurl = getClass().getResource("staticfiles/themes/" + theme2.code + "/theme.css");
			if (themeurl == null)
				this.theme = THEME.getDefault();
			else
				this.theme = theme2;
		}
	}

	public void addNavigationCategory(String link, String name, String title, FredPluginL10n plugin) {
		SubMenu menu = new SubMenu(link, name, title, plugin);
		subMenus.put(name, menu);
		menuList.add(menu);
	}
	

	public void removeNavigationCategory(String name) {
		SubMenu menu = subMenus.remove(name);
		if (menu == null) {
			Logger.error(this, "can't remove navigation category, name="+name);
			return;
		}	
		menuList.remove(menu);
	}
	
	public void addNavigationLink(String menutext, String path, String name, String title, boolean fullOnly, LinkEnabledCallback cb) {
		SubMenu menu = subMenus.get(menutext);
		menu.addNavigationLink(path, name, title, fullOnly, cb);
	}
	
	/* FIXME: Implement a proper way for chosing what the menu looks like upon handleHTTPGet/Post */
	@Deprecated
	public void removeNavigationLink(String menutext, String name) {
		SubMenu menu = subMenus.get(menutext);
		menu.removeNavigationLink(name);
	}
	
	@Deprecated
	public void removeAllNavigationLinks() {
		for(SubMenu menu : subMenus.values())
			menu.removeAllNavigationLinks();
	}
	
	public HTMLNode createBackLink(ToadletContext toadletContext, String name) {
		String referer = toadletContext.getHeaders().get("referer");
		if (referer != null) {
			return new HTMLNode("a", new String[] { "href", "title" }, new String[] { referer, name }, name);
		}
		return new HTMLNode("a", new String[] { "href", "title" }, new String[] { "javascript:back()", name }, name);
	}
	
	public PageNode getPageNode(String title, ToadletContext ctx) {
		return getPageNode(title, true, ctx);
	}

	public PageNode getPageNode(String title, boolean renderNavigationLinks, ToadletContext ctx) {
		boolean fullAccess = ctx == null ? false : ctx.isAllowedFullAccess();
		HTMLNode pageNode = new HTMLNode.HTMLDoctype("html", "-//W3C//DTD XHTML 1.1//EN");
		HTMLNode htmlNode = pageNode.addChild("html", "xml:lang", L10n.getSelectedLanguage().isoCode);
		HTMLNode headNode = htmlNode.addChild("head");
		headNode.addChild("meta", new String[] { "http-equiv", "content" }, new String[] { "Content-Type", "text/html; charset=utf-8" });
		headNode.addChild("title", title + " - Freenet");
		if(override == null)
			headNode.addChild("link", new String[] { "rel", "href", "type", "title" }, new String[] { "stylesheet", "/static/themes/" + theme.code + "/theme.css", "text/css", theme.code });
		else
			headNode.addChild(getOverrideContent());
		for (THEME t: THEME.values()) {
			String themeName = t.code;
			headNode.addChild("link", new String[] { "rel", "href", "type", "media", "title" }, new String[] { "alternate stylesheet", "/static/themes/" + themeName + "/theme.css", "text/css", "screen", themeName });
		}
		
		Toadlet t;
		if (ctx != null) {
			t = ctx.activeToadlet();
			t = t.showAsToadlet();
		} else
			t = null;
		String activePath = "";
		if(t != null) activePath = t.path();
		HTMLNode bodyNode = htmlNode.addChild("body");
		HTMLNode pageDiv = bodyNode.addChild("div", "id", "page");
		HTMLNode topBarDiv = pageDiv.addChild("div", "id", "topbar");
		topBarDiv.addChild("h1", title);
		if (renderNavigationLinks) {
			SubMenu selected = null;
			HTMLNode navbarDiv = pageDiv.addChild("div", "id", "navbar");
			HTMLNode navbarUl = navbarDiv.addChild("ul", "id", "navlist");
			for (SubMenu menu : menuList) {
				HTMLNode subnavlist = new HTMLNode("ul");
				boolean isSelected = false;
				boolean nonEmpty = false;
				for (String navigationLink :  fullAccess ? menu.navigationLinkTexts : menu.navigationLinkTextsNonFull) {
					LinkEnabledCallback cb = menu.navigationLinkCallbacks.get(navigationLink);
					if(cb != null && !cb.isEnabled(ctx)) continue;
					nonEmpty = true;
					String navigationTitle = menu.navigationLinkTitles.get(navigationLink);
					String navigationPath = menu.navigationLinks.get(navigationLink);
					HTMLNode sublistItem;
					if(activePath.equals(navigationPath)) {
						sublistItem = subnavlist.addChild("li", "class", "submenuitem-selected");
						isSelected = true;
					} else {
						sublistItem = subnavlist.addChild("li");
					}
					if(menu.plugin != null) {
						if(navigationTitle != null) navigationTitle = menu.plugin.getString(navigationTitle);
						if(navigationLink != null) navigationLink = menu.plugin.getString(navigationLink);
					} else {
						if(navigationTitle != null) navigationTitle = L10n.getString(navigationTitle);
						if(navigationLink != null) navigationLink = L10n.getString(navigationLink);
					}
					if(navigationTitle != null)
						sublistItem.addChild("a", new String[] { "href", "title" }, new String[] { navigationPath, navigationTitle }, navigationLink);
					else
						sublistItem.addChild("a", "href", navigationPath, navigationLink);
				}
				if(nonEmpty) {
					HTMLNode listItem;
					if(isSelected) {
						selected = menu;
						subnavlist.addAttribute("class", "subnavlist-selected");
						listItem = new HTMLNode("li", "id", "navlist-selected");
					} else {
						subnavlist.addAttribute("class", "subnavlist");
						listItem = new HTMLNode("li");
					}
					String menuItemTitle = menu.defaultNavigationLinkTitle;
					String text = menu.navigationLinkText;
					if(menu.plugin == null) {
						menuItemTitle = L10n.getString(menuItemTitle);
						text = L10n.getString(text);
					} else {
						menuItemTitle = menu.plugin.getString(menuItemTitle);
						text = menu.plugin.getString(text);
					}
					
					listItem.addChild("a", new String[] { "href", "title" }, new String[] { menu.defaultNavigationLink, menuItemTitle }, text);
					listItem.addChild(subnavlist);
					navbarUl.addChild(listItem);
				}
					
			}
			if(selected != null) {
				HTMLNode div = new HTMLNode("div", "id", "selected-subnavbar");
				HTMLNode subnavlist = div.addChild("ul", "id", "selected-subnavbar-list");
				boolean nonEmpty = false;
				for (String navigationLink :  fullAccess ? selected.navigationLinkTexts : selected.navigationLinkTextsNonFull) {
					LinkEnabledCallback cb = selected.navigationLinkCallbacks.get(navigationLink);
					if(cb != null && !cb.isEnabled(ctx)) continue;
					nonEmpty = true;
					String navigationTitle = selected.navigationLinkTitles.get(navigationLink);
					String navigationPath = selected.navigationLinks.get(navigationLink);
					HTMLNode sublistItem;
					if(activePath.equals(navigationPath)) {
						sublistItem = subnavlist.addChild("li", "class", "submenuitem-selected");
					} else {
						sublistItem = subnavlist.addChild("li");
					}
					if(selected.plugin != null) {
						if(navigationTitle != null) navigationTitle = selected.plugin.getString(navigationTitle);
						if(navigationLink != null) navigationLink = selected.plugin.getString(navigationLink);
					} else {
						if(navigationTitle != null) navigationTitle = L10n.getString(navigationTitle);
						if(navigationLink != null) navigationLink = L10n.getString(navigationLink);
					}
					if(navigationTitle != null)
						sublistItem.addChild("a", new String[] { "href", "title" }, new String[] { navigationPath, navigationTitle }, navigationLink);
					else
						sublistItem.addChild("a", "href", navigationPath, navigationLink);
				}
				if(nonEmpty)
					pageDiv.addChild(div);
			}
		}
		HTMLNode contentDiv = pageDiv.addChild("div", "id", "content");
		return new PageNode(pageNode, headNode, contentDiv);
	}

	public InfoboxNode getInfobox(String header) {
		if (header == null) throw new NullPointerException();
		return getInfobox(new HTMLNode("#", header));
	}
	
	public InfoboxNode getInfobox(HTMLNode header) {
		if (header == null) throw new NullPointerException();
		return getInfobox(null, header);
	}

	public InfoboxNode getInfobox(String category, String header) {
		if (header == null) throw new NullPointerException();
		return getInfobox(category, new HTMLNode("#", header));
	}

	/** Create an infobox, attach it to the given parent, and return the content node. */
	public HTMLNode getInfobox(String category, String header, HTMLNode parent) {
		InfoboxNode node = getInfobox(category, header);
		parent.addChild(node.outer);
		return node.content;
	}

	/**
	 * Returns an infobox with the given style and header. If you retrieve an
	 * infobox from this method, be sure to retrieve the matching content node
	 * with {@link #getContentNode(HTMLNode)} otherwise your layout will be
	 * destroyed (and you will get memory leaks).
	 * 
	 * @param category
	 *            The CSS styles, separated by a space (' ')
	 * @param header
	 *            The header HTML node
	 * @return The infobox
	 */
	public InfoboxNode getInfobox(String category, HTMLNode header) {
		if (header == null) throw new NullPointerException();
		HTMLNode infobox = new HTMLNode("div", "class", "infobox" + ((category == null) ? "" : (' ' + category)));
		infobox.addChild("div", "class", "infobox-header").addChild(header);
		return new InfoboxNode(infobox, infobox.addChild("div", "class", "infobox-content"));
	}
	
	private HTMLNode getOverrideContent() {
		HTMLNode result = new HTMLNode("style", "type", "text/css");
		
		try {
			result.addChild("#", FileUtil.readUTF(override));
		} catch (IOException e) {
			Logger.error(this, "Got an IOE: " + e.getMessage(), e);
		}
		
		return result;
	}
	
	/** Call this before getPageNode(), so the menus reflect the advanced mode setting. */
	protected int parseMode(HTTPRequest req, ToadletContainer container) {
		int mode = container.isAdvancedModeEnabled() ? MODE_ADVANCED : MODE_SIMPLE;
		
		if(req.isParameterSet("mode")) {
			mode = req.getIntParam("mode", mode);
			if(mode == MODE_ADVANCED)
				container.setAdvancedMode(true);
			else
				container.setAdvancedMode(false);
		}
		
		return mode;
	}
	
	/** Call this to actually put in the mode selection links */
	protected int drawModeSelectionArray(NodeClientCore core, ToadletContainer container, HTMLNode contentNode, int mode) {
		return drawModeSelectionArray(core, container, contentNode, mode, -1, null, null);
	}
	
	protected int drawModeSelectionArray(NodeClientCore core, ToadletContainer container, HTMLNode contentNode, int mode, int alternateMode, String alternateModeTitleKey, String alternateModeTooltipKey) {
		// FIXME style this properly?
		HTMLNode table = contentNode.addChild("table", "border", "1");
		HTMLNode row = table.addChild("tr");
		HTMLNode cell = row.addChild("td");
		
		if(alternateMode > -1) {
			if(mode != alternateMode)
				cell.addChild("a", new String[] { "href", "title" }, new String[] { "?mode="+alternateMode, L10n.getString(alternateModeTooltipKey) }, L10n.getString(alternateModeTitleKey));
			else
				cell.addChild("b", "title", L10n.getString(alternateModeTooltipKey), L10n.getString(alternateModeTitleKey));
			cell = row.addChild("td");
		}
		
		if(mode != MODE_SIMPLE)
			cell.addChild("a", new String[] { "href", "title" }, new String[] { "?mode=1", l10n("modeSimpleTooltip") }, l10n("modeSimple"));
		else
			cell.addChild("b", "title", l10n("modeSimpleTooltip"), l10n("modeSimple"));
		cell = row.addChild("td");
		if(mode != MODE_ADVANCED)
			cell.addChild("a", new String[] { "href", "title" }, new String[] { "?mode=2", l10n("modeAdvancedTooltip") }, l10n("modeAdvanced"));
		else
			cell.addChild("b", "title", l10n("modeAdvancedTooltip"), l10n("modeAdvanced"));
		return mode;
	}
	
	private static final String l10n(String string) {
		return L10n.getString("PageMaker." + string);
	}
}
