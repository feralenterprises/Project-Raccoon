package playtest.isolation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * FR-006 playtest isolation (kit only; not Raccoon code, never in any production artifact).
 * Java preferences normally live in the Windows registry ({@code HKCU\Software\JavaSoft\Prefs}),
 * shared by every Java program of the user; the kit selects this factory with
 * {@code -Djava.util.prefs.PreferencesFactory=playtest.isolation.KitPreferencesFactory} so they
 * are kept in files under the kit's isolated home instead.
 */
public final class KitPreferencesFactory implements PreferencesFactory
{
	private static final Preferences USER = new Node(null, "", root("user"));
	private static final Preferences SYSTEM = new Node(null, "", root("system"));

	@Override
	public Preferences userRoot()
	{
		return USER;
	}

	@Override
	public Preferences systemRoot()
	{
		return SYSTEM;
	}

	private static Path root(String kind)
	{
		String dir = System.getProperty("raccoon.playtest.prefs.dir");
		if (dir == null || dir.isEmpty())
		{
			dir = Paths.get(System.getProperty("user.home"), "java-prefs").toString();
		}
		return Paths.get(dir, kind);
	}

	private static final class Node extends AbstractPreferences
	{
		private final Path dir;
		private final Properties values = new Properties();

		Node(Node parent, String name, Path dir)
		{
			super(parent, name);
			this.dir = dir;
			Path file = dir.resolve("values.properties");
			if (Files.isRegularFile(file))
			{
				try (InputStream in = Files.newInputStream(file))
				{
					values.load(in);
				}
				catch (IOException e)
				{
					// an unreadable store starts empty; it is test data inside the kit
				}
			}
		}

		@Override
		protected void putSpi(String key, String value)
		{
			values.setProperty(key, value);
		}

		@Override
		protected String getSpi(String key)
		{
			return values.getProperty(key);
		}

		@Override
		protected void removeSpi(String key)
		{
			values.remove(key);
		}

		@Override
		protected void removeNodeSpi() throws BackingStoreException
		{
			try
			{
				if (Files.exists(dir))
				{
					List<Path> paths = new ArrayList<>();
					Files.walk(dir).forEach(paths::add);
					paths.sort(Comparator.reverseOrder());
					for (Path p : paths)
					{
						Files.deleteIfExists(p);
					}
				}
			}
			catch (IOException e)
			{
				throw new BackingStoreException(e);
			}
		}

		@Override
		protected String[] keysSpi()
		{
			return values.stringPropertyNames().toArray(new String[0]);
		}

		@Override
		protected String[] childrenNamesSpi() throws BackingStoreException
		{
			List<String> names = new ArrayList<>();
			if (Files.isDirectory(dir))
			{
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir))
				{
					for (Path p : ds)
					{
						if (Files.isDirectory(p))
						{
							names.add(decode(p.getFileName().toString()));
						}
					}
				}
				catch (IOException e)
				{
					throw new BackingStoreException(e);
				}
			}
			return names.toArray(new String[0]);
		}

		@Override
		protected AbstractPreferences childSpi(String name)
		{
			return new Node(this, name, dir.resolve(encode(name)));
		}

		@Override
		protected void syncSpi() throws BackingStoreException
		{
			flushSpi();
		}

		@Override
		protected void flushSpi() throws BackingStoreException
		{
			Path file = dir.resolve("values.properties");
			try
			{
				if (values.isEmpty() && !Files.exists(file))
				{
					return;
				}
				Files.createDirectories(dir);
				try (OutputStream out = Files.newOutputStream(file))
				{
					values.store(out, null);
				}
			}
			catch (IOException e)
			{
				throw new BackingStoreException(e);
			}
		}

		private static String encode(String name)
		{
			try
			{
				return name.isEmpty() ? "_" : URLEncoder.encode(name, "UTF-8").replace("*", "%2A");
			}
			catch (UnsupportedEncodingException e)
			{
				throw new IllegalStateException(e);
			}
		}

		private static String decode(String name)
		{
			try
			{
				return "_".equals(name) ? "" : URLDecoder.decode(name, "UTF-8");
			}
			catch (UnsupportedEncodingException e)
			{
				throw new IllegalStateException(e);
			}
		}
	}
}
