package com.feralenterprises.raccoon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.BeforeClass;
import org.junit.Test;

public class RaccoonPluginDescriptorTest
{
	private static Properties props;

	@BeforeClass
	public static void loadProperties() throws IOException
	{
		props = new Properties();
		try (InputStream in = new FileInputStream("runelite-plugin.properties"))
		{
			props.load(in);
		}
	}

	@Test
	public void pluginExtendsRuneLitePlugin()
	{
		assertTrue(Plugin.class.isAssignableFrom(RaccoonPlugin.class));
	}

	@Test
	public void descriptorNameMatchesDisplayName()
	{
		PluginDescriptor d = RaccoonPlugin.class.getAnnotation(PluginDescriptor.class);
		assertNotNull("@PluginDescriptor missing", d);
		assertEquals(props.getProperty("displayName"), d.name());
	}

	@Test
	public void propertiesDeclareSingleStandardEntry()
	{
		assertEquals("standard", props.getProperty("build"));
		assertEquals(RaccoonPlugin.class.getName(), props.getProperty("plugins"));
	}

	@Test
	public void descriptorHasNoTemplateLeftovers()
	{
		PluginDescriptor d = RaccoonPlugin.class.getAnnotation(PluginDescriptor.class);
		assertFalse("Example".equals(d.name()));
		assertFalse(props.getProperty("author").isEmpty());
		assertFalse(props.getProperty("description").isEmpty());
	}

	@Test
	public void lifecycleIsSideEffectFree()
	{
		AccountLifecycleService service = new AccountLifecycleService(() -> "LOGIN_SCREEN", () -> -1L,
			java.util.Collections::emptySet, System::nanoTime);
		RaccoonPlugin plugin = new RaccoonPlugin(service);
		plugin.startUp();
		assertFalse(service.view().snapshot().isPublishable());
		plugin.shutDown();
	}
}
