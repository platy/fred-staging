package freenet.pluginmanager;

/**
 * Interface for plugins exposing an API to other plugins
 * @author MikeB
 */
public interface FredPluginAPI {
	/**
	 * Get the API for this plugin
	 * @return some object which can be cast to an interface by other plugins
	 */
	public Object getPluginAPI();
}
