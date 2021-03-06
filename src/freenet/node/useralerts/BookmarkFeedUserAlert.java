package freenet.node.useralerts;

import freenet.keys.FreenetURI;
import freenet.l10n.L10n;
import freenet.node.DarknetPeerNode;
import freenet.node.fcp.ReceivedBookmarkFeed;
import freenet.support.HTMLNode;

public class BookmarkFeedUserAlert extends AbstractUserAlert {
	private final DarknetPeerNode sourcePeerNode;
	private final FreenetURI uri;
	private final String sourceNodeName;
	private final String targetNodeName;
	private final int fileNumber;
	private final String name;
	private final String description;
	private final boolean hasAnActivelink;
	private final long composed;
	private final long sent;
	private final long received;

	public BookmarkFeedUserAlert(DarknetPeerNode sourcePeerNode, String sourceNodeName, String targetNodeName,
			String name, String description, boolean hasAnActivelink, int fileNumber, FreenetURI uri,
			long composed, long sent, long received) {
		super(true, null, null, null, null, UserAlert.MINOR, true, null, true, null);
		this.sourcePeerNode = sourcePeerNode;
		this.name = name;
		this.description = description;
		this.uri = uri;
		this.sourceNodeName = sourceNodeName;
		this.targetNodeName = targetNodeName;
		this.fileNumber = fileNumber;
		this.hasAnActivelink = hasAnActivelink;
		this.composed = composed;
		this.sent = sent;
		this.received = received;
	}

	@Override
	public String getTitle() {
		return l10n("title", "from", sourceNodeName);
	}

	@Override
	public String getText() {
		StringBuilder sb = new StringBuilder();
		sb.append(l10n("peerName")).append("\n").append(name).append("\n");
		sb.append(l10n("bookmarkURI")).append("\n").append(uri).append("\n");
		sb.append(l10n("bookmarkDescription")).append("\n").append(description);
		return sb.toString();
	}

	@Override
	public String getShortText() {
		return getTitle();
	}

	@Override
	public HTMLNode getHTMLText() {
		HTMLNode alertNode = new HTMLNode("div");
		alertNode.addChild("a", "href",
				"/?newbookmark=" + uri + "&desc=" + name + "&hasAnActivelink=" + hasAnActivelink)
				.addChild(
						"img",
						new String[] { "src", "alt", "title" },
						new String[] { "/static/icon/bookmark-new.png", l10n("addAsABookmark"),
								l10n("addAsABookmark") });
		alertNode.addChild("a", "href", uri.toString()).addChild("#", name);
		if (description != null && !description.isEmpty()) {
			String[] lines = description.split("\n");
			alertNode.addChild("br");
			alertNode.addChild("br");
			alertNode.addChild("#", l10n("bookmarkDescription"));
			alertNode.addChild("br");
			for (int i = 0; i < lines.length; i++) {
				alertNode.addChild("#", lines[i]);
				if (i != lines.length - 1)
					alertNode.addChild("br");
			}
		}
		return alertNode;
	}

	@Override
	public String dismissButtonText() {
		return l10n("delete");
	}

	private String l10n(String key) {
		return L10n.getString("BookmarkFeedUserAlert." + key);
	}

	private String l10n(String key, String pattern, String value) {
		return L10n.getString("BookmarkFeedUserAlert." + key, pattern, value);
	}

	@Override
	public void onDismiss() {
		sourcePeerNode.deleteExtraPeerDataFile(fileNumber);
	}

	@Override
	public ReceivedBookmarkFeed getFCPMessage(String identifier) {
		return new ReceivedBookmarkFeed(identifier, getTitle(), getShortText(), getText(), sourceNodeName,
				targetNodeName, composed, sent, received, name, uri, description, hasAnActivelink);
	}
}
