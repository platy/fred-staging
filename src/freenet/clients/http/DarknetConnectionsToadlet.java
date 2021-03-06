package freenet.clients.http;

import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.HashMap;

import freenet.client.HighLevelSimpleClient;
import freenet.l10n.L10n;
import freenet.node.DarknetPeerNode;
import freenet.node.DarknetPeerNodeStatus;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.PeerManager;
import freenet.node.PeerNodeStatus;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.SimpleFieldSet;
import freenet.support.api.HTTPRequest;

public class DarknetConnectionsToadlet extends ConnectionsToadlet {
	
	DarknetConnectionsToadlet(Node n, NodeClientCore core, HighLevelSimpleClient client) {
		super(n, core, client);
	}
	
	@Override
	public String supportedMethods() {
		return "GET, POST";
	}

	private static String l10n(String string) {
		return L10n.getString("DarknetConnectionsToadlet."+string);
	}
	
	protected class DarknetComparator extends ComparatorByStatus {

		DarknetComparator(String sortBy, boolean reversed) {
			super(sortBy, reversed);
		}
	
		@Override
		protected int customCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode, String sortBy) {
			if(sortBy.equals("name")) {
				return ((DarknetPeerNodeStatus)firstNode).getName().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getName());
			}else if(sortBy.equals("privnote")){
				return ((DarknetPeerNodeStatus)firstNode).getPrivateDarknetCommentNote().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getPrivateDarknetCommentNote());
			} else
				return super.customCompare(firstNode, secondNode, sortBy);
		}
		
		/** Default comparison, after taking into account status */
		@Override
		protected int lastResortCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
			return ((DarknetPeerNodeStatus)firstNode).getName().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getName());
		}

	}
	
	@Override
	protected Comparator<PeerNodeStatus> comparator(String sortBy, boolean reversed) {
		return new DarknetComparator(sortBy, reversed);
	}
		
	@Override
	protected boolean hasNameColumn() {
		return true;
	}
	
	@Override
	protected void drawNameColumn(HTMLNode peerRow, PeerNodeStatus peerNodeStatus) {
		// name column
		peerRow.addChild("td", "class", "peer-name").addChild("a", "href", "/send_n2ntm/?peernode_hashcode=" + peerNodeStatus.hashCode(), ((DarknetPeerNodeStatus)peerNodeStatus).getName());
	}

	@Override
	protected boolean hasPrivateNoteColumn() {
		return true;
	}

	@Override
	protected void drawPrivateNoteColumn(HTMLNode peerRow, PeerNodeStatus peerNodeStatus, boolean fProxyJavascriptEnabled) {
		// private darknet node comment note column
		DarknetPeerNodeStatus status = (DarknetPeerNodeStatus) peerNodeStatus;
		if(fProxyJavascriptEnabled) {
			peerRow.addChild("td", "class", "peer-private-darknet-comment-note").addChild("input", new String[] { "type", "name", "size", "maxlength", "onBlur", "onChange", "value" }, new String[] { "text", "peerPrivateNote_" + peerNodeStatus.hashCode(), "16", "250", "peerNoteBlur();", "peerNoteChange();", status.getPrivateDarknetCommentNote() });
		} else {
			peerRow.addChild("td", "class", "peer-private-darknet-comment-note").addChild("input", new String[] { "type", "name", "size", "maxlength", "value" }, new String[] { "text", "peerPrivateNote_" + peerNodeStatus.hashCode(), "16", "250", status.getPrivateDarknetCommentNote() });
		}
	}

	@Override
	protected SimpleFieldSet getNoderef() {
		return node.exportDarknetPublicFieldSet();
	}

	@Override
	protected PeerNodeStatus[] getPeerNodeStatuses(boolean noHeavy) {
		return node.peers.getDarknetPeerNodeStatuses(noHeavy);
	}

	@Override
	protected String getPageTitle(String titleCountString, String myName) {
		return L10n.getString("DarknetConnectionsToadlet.fullTitle", new String[] { "counts", "name" }, new String[] { titleCountString, node.getMyName() } );
	}
	
	@Override
	protected boolean shouldDrawNoderefBox(boolean advancedModeEnabled) {
		return advancedModeEnabled; // Convenient for advanced users, but normally we will use the "Add a friend" box.
	}

	@Override
	protected boolean showPeerActionsBox() {
		return true;
	}

	@Override
	protected void drawPeerActionSelectBox(HTMLNode peerForm, boolean advancedModeEnabled) {
		HTMLNode actionSelect = peerForm.addChild("select", new String[] { "id", "name" }, new String[] { "action", "action" });
		actionSelect.addChild("option", "value", "", l10n("selectAction"));
		actionSelect.addChild("option", "value", "send_n2ntm", l10n("sendMessageToPeers"));
		actionSelect.addChild("option", "value", "update_notes", l10n("updateChangedPrivnotes"));
		if(advancedModeEnabled) {
			actionSelect.addChild("option", "value", "enable", l10n("peersEnable"));
			actionSelect.addChild("option", "value", "disable", l10n("peersDisable"));
			actionSelect.addChild("option", "value", "set_burst_only", l10n("peersSetBurstOnly"));
			actionSelect.addChild("option", "value", "clear_burst_only", l10n("peersClearBurstOnly"));
			actionSelect.addChild("option", "value", "set_listen_only", l10n("peersSetListenOnly"));
			actionSelect.addChild("option", "value", "clear_listen_only", l10n("peersClearListenOnly"));
			actionSelect.addChild("option", "value", "set_allow_local", l10n("peersSetAllowLocal"));
			actionSelect.addChild("option", "value", "clear_allow_local", l10n("peersClearAllowLocal"));
			actionSelect.addChild("option", "value", "set_ignore_source_port", l10n("peersSetIgnoreSourcePort"));
			actionSelect.addChild("option", "value", "clear_ignore_source_port", l10n("peersClearIgnoreSourcePort"));
			actionSelect.addChild("option", "value", "set_dont_route", l10n("peersSetDontRoute"));
			actionSelect.addChild("option", "value", "clear_dont_route", l10n("peersClearDontRoute"));
		}
		actionSelect.addChild("option", "value", "", l10n("separator"));
		actionSelect.addChild("option", "value", "remove", l10n("removePeers"));
		peerForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "doAction", l10n("go") });
	}

	@Override
	protected String getPeerListTitle() {
		return l10n("myFriends");
	}

	@Override
	protected boolean acceptRefPosts() {
		return true;
	}

	@Override
	protected String defaultRedirectLocation() {
		return "/friends/"; // FIXME
	}

	/**
	 * Implement other post actions than adding nodes.
	 * @throws IOException 
	 * @throws ToadletContextClosedException 
	 * @throws RedirectException 
	 */
	@Override
	protected void handleAltPost(URI uri, HTTPRequest request, ToadletContext ctx, boolean logMINOR) throws ToadletContextClosedException, IOException, RedirectException {
		if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("send_n2ntm")) {
			PageNode page = ctx.getPageMaker().getPageNode(l10n("sendMessageTitle"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			HashMap<String, String> peers = new HashMap<String, String>();
			for(DarknetPeerNode pn : peerNodes) {
				if (request.isPartSet("node_"+pn.hashCode())) {
					String peer_name = pn.getName();
					String peer_hash = "" + pn.hashCode();
					if(!peers.containsKey(peer_hash)) {
						peers.put(peer_hash, peer_name);
					}
				}
			}
			N2NTMToadlet.createN2NTMSendForm( pageNode, contentNode, ctx, peers);
			writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("update_notes")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("peerPrivateNote_"+peerNodes[i].hashCode())) {
					if(!request.getPartAsString("peerPrivateNote_"+peerNodes[i].hashCode(),250).equals(peerNodes[i].getPrivateDarknetCommentNote())) {
						peerNodes[i].setPrivateDarknetCommentNote(request.getPartAsString("peerPrivateNote_"+peerNodes[i].hashCode(),250));
					}
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("enable")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].enablePeer();
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("disable")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].disablePeer();
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("set_burst_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setBurstOnly(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("clear_burst_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setBurstOnly(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("set_ignore_source_port")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setIgnoreSourcePort(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("clear_ignore_source_port")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setIgnoreSourcePort(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("clear_dont_route")) {
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setRoutingStatus(true, true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("set_dont_route")) {
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if(request.isPartSet("node_" + peerNodes[i].hashCode())) {
					peerNodes[i].setRoutingStatus(false, true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("set_listen_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setListenOnly(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("clear_listen_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setListenOnly(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("set_allow_local")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setAllowLocalAddresses(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("clear_allow_local")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setAllowLocalAddresses(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("remove") || (request.isPartSet("doAction") && request.getPartAsString("action",25).equals("remove"))) {			
			if(logMINOR) Logger.minor(this, "Remove node");
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {	
					if((peerNodes[i].timeLastConnectionCompleted() < (System.currentTimeMillis() - 1000*60*60*24*7) /* one week */) ||  (peerNodes[i].peerNodeStatus == PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED) || request.isPartSet("forceit")){
						this.node.removePeerConnection(peerNodes[i]);
						if(logMINOR) Logger.minor(this, "Removed node: node_"+peerNodes[i].hashCode());
					}else{
						if(logMINOR) Logger.minor(this, "Refusing to remove : node_"+peerNodes[i].hashCode()+" (trying to prevent network churn) : let's display the warning message.");
						PageNode page = ctx.getPageMaker().getPageNode(l10n("confirmRemoveNodeTitle"), ctx);
						HTMLNode pageNode = page.outer;
						HTMLNode contentNode = page.content;
						HTMLNode content =ctx.getPageMaker().getInfobox("infobox-warning", l10n("confirmRemoveNodeWarningTitle"), contentNode); 
						content.addChild("p").addChild("#",
								L10n.getString("DarknetConnectionsToadlet.confirmRemoveNode", new String[] { "name" }, new String[] { peerNodes[i].getName() }));
						HTMLNode removeForm = ctx.addFormChild(content, "/friends/", "removeConfirmForm");
						removeForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "node_"+peerNodes[i].hashCode(), "remove" });
						removeForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "cancel", L10n.getString("Toadlet.cancel") });
						removeForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "remove", l10n("remove") });
						removeForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "forceit", l10n("forceRemove") });

						writeHTMLReply(ctx, 200, "OK", pageNode.generate());
						return; // FIXME: maybe it breaks multi-node removing
					}				
				} else {
					if(logMINOR) Logger.minor(this, "Part not set: node_"+peerNodes[i].hashCode());
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("acceptTransfer")) {
			// FIXME this is ugly, should probably move both this code and the PeerNode code somewhere.
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					long id = Long.parseLong(request.getPartAsString("id", 32)); // FIXME handle NumberFormatException
					peerNodes[i].acceptTransfer(id);
					break;
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("rejectTransfer")) {
			// FIXME this is ugly, should probably move both this code and the PeerNode code somewhere.
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					long id = Long.parseLong(request.getPartAsString("id", 32)); // FIXME handle NumberFormatException
					peerNodes[i].rejectTransfer(id);
					break;
				}
			}
			redirectHere(ctx);
			return;
		} else {
			this.handleGet(uri, new HTTPRequestImpl(uri, "GET"), ctx);
		}
	}

	private void redirectHere(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/friends/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	@Override
	protected boolean isOpennet() {
		return false;
	}

	@Override
	SimpleColumn[] endColumnHeaders(boolean advancedMode) {
		return null;
	}

	@Override
	public String path() {
		return "/friends/";
	}

}
