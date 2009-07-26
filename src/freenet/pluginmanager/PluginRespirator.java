package freenet.pluginmanager;

import java.net.URISyntaxException;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.ToadletContainer;
import freenet.clients.http.filter.FilterCallback;
import freenet.node.Node;
import freenet.node.RequestStarter;
import freenet.support.HTMLNode;
import freenet.support.URIPreEncoder;

public class PluginRespirator {
	private final HighLevelSimpleClient hlsc;
	private final Node node;
	private final PageMaker pageMaker;
	private final FredPlugin plugin;
	private final PluginManager pluginManager;
	
	public PluginRespirator(Node node, PluginManager pm, FredPlugin plug) {
		this.node = node;
		this.hlsc = node.clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS);
		this.plugin = plug;
		this.pluginManager = pm;
//		if (plugin instanceof FredPluginL10n)
//			pageMaker = new PageMaker((FredPluginL10n)plugin, pluginManager.getFProxyTheme());
//		else
//			pageMaker = new PageMaker(null, pluginManager.getFProxyTheme());
		pageMaker = null; // FIXME!
	}
	
	//public HighLevelSimpleClient getHLSimpleClient() throws PluginSecurityException {
	public HighLevelSimpleClient getHLSimpleClient() {
		return hlsc;
	}
	
	public Node getNode(){
		return node;
	}

	public FilterCallback makeFilterCallback(String path) {
		try {
			return node.clientCore.createFilterCallback(URIPreEncoder.encodeURI(path), null);
		} catch (URISyntaxException e) {
			throw new Error(e);
		}
	}
	
	public PageMaker getPageMaker(){
		ToadletContainer container = getToadletContainer();
		if(container == null) return null;
		return container.getPageMaker();
	}
	
	public HTMLNode addFormChild(HTMLNode parentNode, String target, String name) {
		HTMLNode formNode =
			parentNode.addChild("form", new String[] { "action", "method", "enctype", "id", "name", "accept-charset" }, 
					new String[] { target, "post", "multipart/form-data", name, name, "utf-8"} );
		formNode.addChild("input", new String[] { "type", "name", "value" }, 
				new String[] { "hidden", "formPassword", node.clientCore.formPassword });
		
		return formNode;
	}
	
	public PluginTalker getPluginTalker(FredPluginTalker fpt, String pluginname, String identifier) throws PluginNotFoundException {
		return new PluginTalker(fpt, node, pluginname, identifier);
	}

	/**
	 * Get the API of another plugin
	 * @param pluginname classname of main class of other plugin
	 * @return API
	 * @throws freenet.pluginmanager.PluginNotFoundException
	 */
	public Object getPluginAPI(String pluginname) throws PluginNotFoundException {
		return pluginManager.getPluginAPI(pluginname);
	}

	public ToadletContainer getToadletContainer() {
		return node.clientCore.getToadletContainer();
	}

}
