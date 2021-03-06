package freenet.node.fcp;

import java.io.UnsupportedEncodingException;

import freenet.node.DarknetPeerNode;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.SimpleFieldSet;

public class SendTextFeedMessage extends SendFeedMessage {

	public static final String NAME = "SendTextFeed";
	private byte[] text;

	public SendTextFeedMessage(SimpleFieldSet fs) throws MessageInvalidException {
		super(fs);
		String encodedText = fs.get("Text");
		if (encodedText == null)
			throw new MessageInvalidException(ProtocolErrorMessage.MISSING_FIELD, "No text", identifier,
					false);
		try {
			text = Base64.decode(encodedText);
		} catch (IllegalBase64Exception e) {
			throw new MessageInvalidException(ProtocolErrorMessage.INVALID_FIELD, e.getMessage(),
					identifier, false);
		}
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		SimpleFieldSet fs = super.getFieldSet();
		fs.putSingle("Text", Base64.encode(text));
		return fs;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	protected int handleFeed(DarknetPeerNode pn) {
		try {
			return pn.sendTextFeed(new String(text, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			throw new Error("Impossible: JVM doesn't support UTF-8: " + e, e);
		}
	}
}