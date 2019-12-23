package gui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Properties;

import javax.swing.UIManager;

/**
 * Локализация текста
 */
public final class Localization {
	private static final String TEXT_ENCODE = "UTF-8";
	static final String[][] LANGUAGES = {
		{ "ru", "Русский" },
		{ "en", "English" },
	};
	public static final Properties sStrings = new Properties();
	//static String sLanguage = "";
	public static Locale sLocale = Locale.getDefault();
	/**
	 * Локализация встроенных диалогов
	 * @param name - название ресурса
	 * @param language - код языка
	 */
	static void loadUIManagerLocalization(final String name, final String language) {
		InputStreamReader ir = null;
		try {
			final InputStream is = Localization.class.getResourceAsStream( "/res/" + name + "_" + language + ".txt" );
			if( is == null ) {
				return;
			}
			ir = new InputStreamReader( is, TEXT_ENCODE );
			final Properties p = new Properties();
			p.load( ir );
			for( final Object k : p.keySet() ) {
				UIManager.put( (String)k, p.getProperty( (String)k ) );
			}
		} catch(final UnsupportedEncodingException e) {
		} catch(final IOException e) {
		} finally {
			if( ir != null ) {
				try { ir.close(); } catch( final IOException e ) {}
			}
		}
	}
	/**
	 * Установить используемый язык
	 * @param language - код языка
	 */
	public static void setLanguage(final String language) {
		InputStreamReader ir = null;
		try {
			ir = new InputStreamReader(
					Localization.class.getResourceAsStream( "/res/" + language + ".txt" ),
					TEXT_ENCODE );
			sStrings.load( ir );
			sLocale = new Locale( language );
		} catch(final UnsupportedEncodingException e) {
		} catch(final IOException e) {
			if( ir != null ) {
				try { ir.close(); } catch( final IOException ie ) {}
			}
			try {
				ir = new InputStreamReader(
						Localization.class.getResourceAsStream("/res/en.txt"),
						TEXT_ENCODE );
				sStrings.load( ir );
				sLocale = new Locale( "en" );
			} catch( final UnsupportedEncodingException ue ) {
			} catch( final IOException ie ) {
			}
		} catch(final Exception e) {
		} finally {
			if( ir != null ) {
				try { ir.close(); } catch( final IOException e ) {}
			}
		}
		// локализация встроенных диалогов
		/*final java.util.Enumeration<Object> en = UIManager.getLookAndFeelDefaults().keys();
		while( en.hasMoreElements() ) {
			final Object o = en.nextElement();
			if( o instanceof String && ((String)o).startsWith( "ColorChooser" ) ) {
				System.out.println( "key = " + o + ", val = " + UIManager.get( o ) );
			}
		}*/
		// FIXME bug: JI-9012954 невозможно полностью перевести диалог FileChooser
		// List, Details не переводятся
		loadUIManagerLocalization( "OptionPane", language );
		loadUIManagerLocalization( "FileChooser", language );
		loadUIManagerLocalization( "ColorChooser", language );
	}
	/**
	 * Получить локализованную версию строки
	 * @param msg - сообщение
	 * @return локализованная версия. Если такой нет, возвращается исходное сообщение
	 */
	public static final String get(final String msg) {
		//if( sStrings == null ) return msg;
		return sStrings.getProperty( msg, msg );
	}
	/**
	 * Получить локализованные версии строк
	 * @param prefix префикс, который надо добавить к названиям ключей
	 * @param keys список ключей строк
	 * @return локализованный список
	 */
	public static final String[] get(final String prefix, final String[] keys) {
		final int length = keys.length;
		final String[] str = new String[length];
		for( int i = 0; i < length; i++ ) {
			str[i] = get( prefix + keys[i] );
		}
		return str;
	}
	/**
	 * Получить локализованную версию отформатированной строки
	 * @param format - формат, например, "текст {0} текст {1}"
	 * @param parameters - список параметров
	 * @return локализованная версия строки
	 */
	public static final String format(String format, final Object[] parameters) {
		if( sStrings == null ) {
			return format;
		}
		format = sStrings.getProperty( format, format );
		if( parameters == null || parameters.length == 0 ) {
			// No parameters.  Just return format string.
			return format;
		}
		try {
			// Is is a java.text style format?
			// Ideally we could match with
			// Pattern.compile("\\{\\d").matcher(format).find())
			// However the cost is 14% higher, so we cheaply check for
			// 1 of the first 4 parameters
			if( format.indexOf("{0") >= 0 || format.indexOf("{1") >= 0 ||
					format.indexOf("{2") >= 0 || format.indexOf("{3") >= 0 ) {
				return java.text.MessageFormat.format( format, parameters );
			}
		} catch(final Exception e) {
		}
		return format;
	}
	/**
	 * Получить локализованную версию отформатированной строки с одним аргументом
	 * @param format формат, например, "текст {0}"
	 * @param param параметр
	 * @return локализованная версия строки
	 */
	public static final String format(final String format, final Object param) {
		return format( format, new Object[] { param } );
	}
	/**
	 * Получить локализованную версию отформатированной строки с двумя аргументами
	 * @param format формат, например, "текст {0} текст {1}"
	 * @param param1 параметр 1
	 * @param param2 параметр 2
	 * @return локализованная версия строки
	 */
	public static final String format(final String format, final Object param1, final Object param2) {
		return format( format, new Object[] { param1, param2 } );
	}
}