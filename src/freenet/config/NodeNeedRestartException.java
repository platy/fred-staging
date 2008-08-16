/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.config;

/**
 * Thrown when the node refuses to set a config variable to a particular
 * value because it is invalid. Just because this is not thrown does not
 * necessarily mean that there are no problems with the value defined,
 * it merely means that there are no immediately detectable problems with 
 * it.
 */
public class NodeNeedRestartException extends ConfigException {

	public NodeNeedRestartException(String msg) {
		super(msg);
	}

}