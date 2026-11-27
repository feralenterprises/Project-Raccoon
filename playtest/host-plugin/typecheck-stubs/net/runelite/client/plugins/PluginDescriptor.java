package net.runelite.client.plugins;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
public @interface PluginDescriptor {
	String name();
	String description() default "";
	boolean enabledByDefault() default true;
	boolean hidden() default false;
	boolean developerPlugin() default false;
	boolean loadInSafeMode() default true;
}
