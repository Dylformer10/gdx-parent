/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.PropertyMap;
import com.badlogic.gdx.utils.StreamUtils;
import com.badlogic.gdx.utils.StringBuilder;
import com.badlogic.gdx.utils.reflect.ClassReflection;

/** A {@code I18NBundle} provides {@code Locale}-specific resources loaded from property files. A bundle contains a number of named
 * resources, whose names and values are {@code Strings}. A bundle may have a parent bundle, and when a resource is not found in a
 * bundle, the parent bundle is searched for the resource. If the fallback mechanism reaches the base bundle and still can't find
 * the resource it throws a {@code MissingResourceException}.
 * 
 * <ul>
 * <li>All bundles for the same group of resources share a common base bundle. This base bundle acts as the root and is the last
 * fallback in case none of its children was able to respond to a request.</li>
 * <li>The first level contains changes between different languages. Only the differences between a language and the language of
 * the base bundle need to be handled by a language-specific {@code I18NBundle}.</li>
 * <li>The second level contains changes between different countries that use the same language. Only the differences between a
 * country and the country of the language bundle need to be handled by a country-specific {@code I18NBundle}.</li>
 * <li>The third level contains changes that don't have a geographic reason (e.g. changes that where made at some point in time
 * like {@code PREEURO} where the currency of come countries changed. The country bundle would return the current currency (Euro)
 * and the {@code PREEURO} variant bundle would return the old currency (e.g. DM for Germany).</li>
 * </ul>
 * 
 * <strong>Examples</strong>
 * <ul>
 * <li>BaseName (base bundle)
 * <li>BaseName_de (german language bundle)
 * <li>BaseName_fr (french language bundle)
 * <li>BaseName_de_DE (bundle with Germany specific resources in german)
 * <li>BaseName_de_CH (bundle with Switzerland specific resources in german)
 * <li>BaseName_fr_CH (bundle with Switzerland specific resources in french)
 * <li>BaseName_de_DE_PREEURO (bundle with Germany specific resources in german of the time before the Euro)
 * <li>BaseName_fr_FR_PREEURO (bundle with France specific resources in french of the time before the Euro)
 * </ul>
 * 
 * It's also possible to create variants for languages or countries. This can be done by just skipping the country or language
 * abbreviation: BaseName_us__POSIX or BaseName__DE_PREEURO. But it's not allowed to circumvent both language and country:
 * BaseName___VARIANT is illegal.
 * 
 * @see PropertyMap
 * 
 * @author davebaol */
public abstract class I18NBundle {

	private static final String LOG_TAG = I18NBundle.class.getSimpleName();

	private static final String DEFAULT_ENCODING = "ISO-8859-1";

	/** The parent of this {@code I18NBundle} that is used if this bundle doesn't include the requested resource. */
	protected I18NBundle parent;

	/** The locale for this bundle. */
	private Locale locale;

	/** The properties for this bundle. */
	private PropertyMap properties;

	/** Creates a new bundle using the specified <code>class</code> and <code>baseFileHandle</code>, the default locale and the
	 * default encoding "ISO-8859-1".
	 * 
	 * @param clazz the class of the bundle to be returned
	 * @param baseFileHandle the file handle to the base of the bundle
	 * @exception NullPointerException if <code>baseFileHandle</code> is <code>null</code>
	 * @exception MissingResourceException if no bundle for the specified base file handle can be found
	 * @return a bundle for the given base file handle and the default locale */
	public static <T extends I18NBundle> T createBundle (Class<T> clazz, FileHandle baseFileHandle) {
		return createBundleImpl(clazz, baseFileHandle, Locale.getDefault(), DEFAULT_ENCODING);
	}

	/** Creates a new bundle using the specified <code>class</code>, <code>baseFileHandle</code> and <code>locale</code>; the
	 * default encoding "ISO-8859-1" is used.
	 * 
	 * @param clazz the class of the bundle to be returned
	 * @param baseFileHandle the file handle to the base of the bundle
	 * @param locale the locale for which a bundle is desired
	 * @return a bundle for the given base file handle and locale
	 * @exception NullPointerException if <code>baseFileHandle</code> or <code>locale</code> is <code>null</code>
	 * @exception MissingResourceException if no bundle for the specified base file handle can be found */
	public static <T extends I18NBundle> T createBundle (Class<T> clazz, FileHandle baseFileHandle, Locale locale) {
		return createBundleImpl(clazz, baseFileHandle, locale, DEFAULT_ENCODING);
	}

	/** Creates a new bundle using the specified <code>class</code>, <code>baseFileHandle</code> and <code>encoding</code>; the
	 * default locale is used.
	 * 
	 * @param baseFileHandle the file handle to the base of the bundle
	 * @param encoding the charter encoding
	 * @return a bundle for the given base file handle and locale
	 * @exception NullPointerException if <code>baseFileHandle</code> or <code>encoding</code> is <code>null</code>
	 * @exception MissingResourceException if no bundle for the specified base file handle can be found */
	public static <T extends I18NBundle> T createBundle (Class<T> clazz, FileHandle baseFileHandle, String encoding) {
		return createBundleImpl(clazz, baseFileHandle, Locale.getDefault(), encoding);
	}

	/** Creates a new bundle using the specified <code>class</code>, <code>baseFileHandle</code>, <code>locale</code> and
	 * <code>encoding</code>.
	 * 
	 * @param clazz the class of the bundle to be returned
	 * @param baseFileHandle the file handle to the base of the bundle
	 * @param locale the locale for which a bundle is desired
	 * @param encoding the charter encoding
	 * @return a bundle for the given base file handle and locale
	 * @exception NullPointerException if <code>baseFileHandle</code>, <code>locale</code> or <code>encoding</code> is
	 *               <code>null</code>
	 * @exception MissingResourceException if no bundle for the specified base file handle can be found */
	public static <T extends I18NBundle> T createBundle (Class<T> clazz, FileHandle baseFileHandle, Locale locale, String encoding) {
		return createBundleImpl(clazz, baseFileHandle, locale, encoding);
	}

	// TODO Remove this method. It just logs the parent chain after calling the actual implementation.
	private static <T extends I18NBundle> T createBundleImpl (Class<T> clazz, FileHandle baseFileHandle, Locale locale,
		String encoding) {
		T rb = createBundleImpl2(clazz, baseFileHandle, locale, encoding);
		// Log the requested locale and the parent chain
		StringBuffer sb = new StringBuffer("Requested locale = ");
		sb.append(locale.equals(Locale.ROOT) ? "root" : locale.toString()).append(", bundle chain = [ ");
		for (I18NBundle p = rb; p != null; p = p.parent) {
			String loc = p.locale.equals(Locale.ROOT) ? "root" : p.locale.toString();
			sb.append(loc).append(" ");
		}
		sb.append("]");
		Gdx.app.log(LOG_TAG, sb.toString());
		return rb;
	}

	private static <T extends I18NBundle> T createBundleImpl2 (Class<T> clazz, FileHandle baseFileHandle, Locale locale,
		String encoding) {
		if (baseFileHandle == null || locale == null || encoding == null) throw new NullPointerException();

		T bundle = null;
		T baseBundle = null;
		Locale targetLocale = locale;
		do {
			// Create the candidate locales
			List<Locale> candidateLocales = getCandidateLocales(targetLocale);

			// Load the bundle and its parents recursively
			bundle = loadBundleChain(clazz, baseFileHandle, encoding, candidateLocales, 0, baseBundle);

			// Check the loaded bundle (if any)
			if (bundle != null) {
				Locale bundleLocale = bundle.getLocale(); // WTH?  GWT can't access bundle.locale directly
				boolean isBaseBundle = bundleLocale.equals(Locale.ROOT);

				if (!isBaseBundle || bundleLocale.equals(locale)) {
					// Found the bundle for the requested locale
					break;
				}
				if (candidateLocales.size() == 1 && bundleLocale.equals(candidateLocales.get(0))) {
					// Found the bundle for the only candidate locale
					break;
				}
				if (isBaseBundle && baseBundle == null) {
					// Store the base bundle and keep on processing the remaining fallback locales
					baseBundle = bundle;
				}
			}

			// Set next fallback locale
			targetLocale = getFallbackLocale(targetLocale);

		} while (targetLocale != null);

		if (bundle == null) {
			if (baseBundle == null) {
				// No bundle found
				throw new MissingResourceException("Can't find bundle for base file handle " + baseFileHandle.path() + ", locale "
					+ locale, baseFileHandle + "_" + locale, "");
			}
			// Set the base bundle to be returned
			bundle = baseBundle;
		}

		return bundle;
	}

	/** Returns a <code>List</code> of <code>Locale</code>s as candidate locales for the given <code>locale</code>. This method is
	 * called by the <code>createBundle</code> factory method each time the factory method tries finding a resource bundle for a
	 * target <code>Locale</code>.
	 * 
	 * <p>
	 * The sequence of the candidate locales also corresponds to the runtime resource lookup path (also known as the <I>parent
	 * chain</I>), if the corresponding resource bundles for the candidate locales exist and their parents are not defined by
	 * loaded resource bundles themselves. The last element of the list is always the {@linkplain Locale#ROOT root locale}, meaning
	 * that the base bundle is the terminal of the parent chain.
	 * 
	 * <p>
	 * If the given locale is equal to <code>Locale.ROOT</code> (the root locale), a <code>List</code> containing only the root
	 * <code>Locale</code> is returned. In this case, the <code>createBundle</code> factory method loads only the base bundle as
	 * the resulting resource bundle.
	 * 
	 * <p>
	 * This implementation returns a <code>List</code> containing <code>Locale</code>s in the following sequence:
	 * 
	 * <pre>
	 *     Locale(language, country, variant)
	 *     Locale(language, country)
	 *     Locale(language)
	 *     Locale.ROOT
	 * </pre>
	 * 
	 * where <code>language</code>, <code>country</code> and <code>variant</code> are the language, country and variant values of
	 * the given <code>locale</code>, respectively. Locales where the final component values are empty strings are omitted.
	 * 
	 * <p>
	 * For example, if the given base name is "Messages" and the given <code>locale</code> is
	 * <code>Locale("ja",&nbsp;"",&nbsp;"XX")</code>, then a <code>List</code> of <code>Locale</code>s:
	 * 
	 * <pre>
	 *     Locale("ja", "", "XX")
	 *     Locale("ja")
	 *     Locale.ROOT
	 * </pre>
	 * 
	 * is returned. And if the resource bundles for the "ja" and "" <code>Locale</code>s are found, then the runtime resource
	 * lookup path (parent chain) is:
	 * 
	 * <pre>
	 *     Messages_ja -> Messages
	 * </pre>
	 * 
	 * @param locale the locale for which a resource bundle is desired
	 * @return a <code>List</code> of candidate <code>Locale</code>s for the given <code>locale</code>
	 * @exception NullPointerException if <code>locale</code> is <code>null</code> */
	private static List<Locale> getCandidateLocales (Locale locale) {
		String language = locale.getLanguage();
		String country = locale.getCountry();
		String variant = locale.getVariant();

		List<Locale> locales = new ArrayList<Locale>(4);
		if (variant.length() > 0) {
			locales.add(locale);
		}
		if (country.length() > 0) {
			locales.add((locales.size() == 0) ? locale : new Locale(language, country));
		}
		if (language.length() > 0) {
			locales.add((locales.size() == 0) ? locale : new Locale(language));
		}
		locales.add(Locale.ROOT);
		return locales;
	}

	/** Returns a <code>Locale</code> to be used as a fallback locale for further bundle searches by the <code>createBundle</code>
	 * factory method. This method is called from the factory method every time when no resulting bundle has been found for
	 * <code>baseFileHandler</code> and <code>locale</code>, where locale is either the parameter for <code>createBundle</code> or
	 * the previous fallback locale returned by this method.
	 * 
	 * <p>
	 * This method returns the {@linkplain Locale#getDefault() default <code>Locale</code>} if the given <code>locale</code> isn't
	 * the default one. Otherwise, <code>null</code> is returned.
	 * 
	 * @param locale the <code>Locale</code> for which <code>createBundle</code> has been unable to find any resource bundles
	 *           (except for the base bundle)
	 * @return a <code>Locale</code> for the fallback search, or <code>null</code> if no further fallback search is needed.
	 * @exception NullPointerException if <code>locale</code> is <code>null</code> */
	private static Locale getFallbackLocale (Locale locale) {
		Locale defaultLocale = Locale.getDefault();
		return locale.equals(defaultLocale) ? null : defaultLocale;
	}

	private static final <T extends I18NBundle> T loadBundleChain (Class<T> clazz, FileHandle baseFileHandle, String encoding,
		List<Locale> candidateLocales, int candidateIndex, T baseBundle) {
		Locale targetLocale = candidateLocales.get(candidateIndex);
		T parent = null;
		if (candidateIndex != candidateLocales.size() - 1) {
			// Load recursively the parent having the next candidate locale
			parent = loadBundleChain(clazz, baseFileHandle, encoding, candidateLocales, candidateIndex + 1, baseBundle);
		} else if (baseBundle != null && targetLocale.equals(Locale.ROOT)) {
			return baseBundle;
		}

		// Load the bundle
		T bundle = loadBundle(clazz, baseFileHandle, encoding, targetLocale);
		if (bundle != null) {
			bundle.parent = parent;
// bundle.locale = targetLocale;
			return bundle;
		}

		return parent;
	}

	// Tries to load the bundle for the given locale.
	private static final <T extends I18NBundle> T loadBundle (Class<T> clazz, FileHandle baseFileHandle, String encoding,
		Locale targetLocale) {
		T bundle = null;
		try {
			FileHandle fileHandle = toFileHandle(baseFileHandle, targetLocale);
			if (fileHandle.exists()) {
				InputStream stream = null;
				try {
					// Instantiate the bundle
					bundle = ClassReflection.newInstance(clazz);

					// Load bundle properties from the stream with the specified encoding
					stream = fileHandle.read();
					bundle.load(new InputStreamReader(stream, encoding));
				} finally {
					StreamUtils.closeQuietly(stream);
				}
			}
		} catch (Exception exception) {
			throw new GdxRuntimeException(exception);
		}
		if (bundle != null) {
			bundle.setLocale(targetLocale);
		}

		return bundle;
	}

	/** Load the underlying {@link PropertyMap} from the specified reader.
	 * 
	 * @param reader the reader
	 * @throws IOException if an error occurred when reading from the input stream.
	 */
	// NOTE:
	// This method can't be private otherwise GWT can't access it from loadBundle() 
	protected void load (Reader reader) throws IOException {
		properties = new PropertyMap();
		properties.load(reader);
	}

	/** Converts the given <code>baseFileHandle</code> and <code>locale</code> to the corresponding file handle.
	 * 
	 * <p>
	 * This implementation returns the <code>baseFileHandle</code>'s sibling with following value:
	 * 
	 * <pre>
	 * baseFileHandle.name() + &quot;_&quot; + language + &quot;_&quot; + country + &quot;_&quot; + variant + &quot;.properties&quot;
	 * </pre>
	 * 
	 * where <code>language</code>, <code>country</code> and <code>variant</code> are the language, country and variant values of
	 * <code>locale</code>, respectively. Final component values that are empty Strings are omitted along with the preceding '_'.
	 * If all of the values are empty strings, then <code>baseFileHandle.name()</code> is returned with ".properties" appended.
	 * 
	 * @param baseFileHandle the file handle to the base of the bundle
	 * @param locale the locale for which a resource bundle should be loaded
	 * @return the file handle for the bundle
	 * @exception NullPointerException if <code>baseFileHandle</code> or <code>locale</code> is <code>null</code> */
	private static FileHandle toFileHandle (FileHandle baseFileHandle, Locale locale) {
		StringBuilder sb = new StringBuilder(baseFileHandle.name());
		if (!locale.equals(Locale.ROOT)) {
			String language = locale.getLanguage();
			String country = locale.getCountry();
			String variant = locale.getVariant();
			boolean emptyLanguage = "".equals(language);
			boolean emptyCountry = "".equals(country);
			boolean emptyVariant = "".equals(variant);

			if (!(emptyLanguage && emptyCountry && emptyVariant)) {
				sb.append('_');
				if (!emptyVariant) {
					sb.append(language).append('_').append(country).append('_').append(variant);
				} else if (!emptyCountry) {
					sb.append(language).append('_').append(country);
				} else {
					sb.append(language);
				}
			}
		}
		return baseFileHandle.sibling(sb.append(".properties").toString());
	}

	/** Returns the locale of this bundle. This method can be used after a call to <code>createBundle()</code> to determine whether
	 * the resource bundle returned really corresponds to the requested locale or is a fallback.
	 * 
	 * @return the locale of this bundle */
	public Locale getLocale () {
		return locale;
	}

	/** Sets the bundle locale. This method is protected because a bundle can't change locale during its life. However, this method
	 * is called by the <code>createBundle</code> factory method after bundle creation so giving subclasses that override it the
	 * opportunity to set themselves based on the locale.
	 * 
	 * @param locale */
	protected void setLocale (Locale locale) {
		this.locale = locale;
	}

	/** Gets a string for the given key from this bundle or one of its parents.
	 * 
	 * @param key the key for the desired string
	 * @exception NullPointerException if <code>key</code> is <code>null</code>
	 * @exception MissingResourceException if no string for the given key can be found
	 * @return the string for the given key */
	public final String get (String key) {
		String result = properties.get(key);
		if (result == null) {
			if (parent != null) result = parent.get(key);
			if (result == null) throw new MissingResourceException("Can't find bundle key " + key, this.getClass().getName(), key);
		}
		return result;
	}

	/** Gets the string with the specified key from this bundle or one of its parent after replacing the given arguments if they
	 * occur.
	 * <p>
	 * Depending on the actual implementation of the bundle argument replacement may be sensitive to the bundle's locale.
	 * 
	 * @param key the key for the desired string
	 * @param args the arguments to be replaced in the string associated to the given key.
	 * @exception NullPointerException if <code>key</code> is <code>null</code>
	 * @exception MissingResourceException if no string for the given key can be found
	 * @return the string for the given key formatted with the given arguments */
	public String format (String key, Object... args) {
		return formatPattern(get(key), args);
	}

	protected abstract String formatPattern (String pattern, Object[] args);

}
