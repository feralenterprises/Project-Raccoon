package com.feralenterprises.raccoon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.feralenterprises.raccoon.account.AccountLifecycleService;
import com.feralenterprises.raccoon.bank.BankObservationService;
import com.feralenterprises.raccoon.kc.BrutusKcService;
import com.feralenterprises.raccoon.core.bank.BankCaptureSource;
import com.feralenterprises.raccoon.core.bank.BankFrame;
import com.feralenterprises.raccoon.core.bank.BankVisibility;
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
		BankObservationService bank = new BankObservationService(service, new BankCaptureSource()
		{
			@Override
			public BankVisibility visibility()
			{
				throw new AssertionError("no reads while signed out");
			}

			@Override
			public BankFrame capture(com.feralenterprises.raccoon.core.account.AccountScope scope, long tick)
			{
				throw new AssertionError("no capture while signed out");
			}
		}, () -> 1);
		RaccoonPlugin plugin = new RaccoonPlugin(service, bank, new BrutusKcService(service));
		plugin.startUp();
		assertFalse(service.view().snapshot().isPublishable());
		plugin.shutDown();
	}
}
