package com.solt.media.config;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import com.solt.media.util.StringUtils;

/**
 * @author thienlong
 * 
 */
public class ConfigurationManager {
	private static final String CONFIG_FILE = "";
	private Properties props;
	private static ConfigurationManager conf = new ConfigurationManager();
	
	private ConfigurationManager() {
		try {
			load();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void load() throws IOException {
		Reader reader = null;
		try {
			reader = new FileReader(CONFIG_FILE);
			props.load(reader);
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}

	public void save() throws IOException {
		Writer writer = null;
		try {
			writer = new FileWriter(CONFIG_FILE);
			props.store(writer, null);
		} finally {
			if (writer != null) {
				writer.close();
			}
		}
	}
	
	public static ConfigurationManager getInstance() {
		return conf;
	}

	public String get(String name) {
		return props.getProperty(name);
	}

	public String get(String name, String defaultValue) {
		return props.getProperty(name, defaultValue);
	}

	/**
	 * Get the value of the <code>name</code> property as a trimmed
	 * <code>String</code>, <code>null</code> if no such property exists. If the
	 * key is deprecated, it returns the value of the first key which replaces
	 * the deprecated key and is not null
	 * 
	 * Values are processed for <a href="#VariableExpansion">variable
	 * expansion</a> before being returned.
	 * 
	 * @param name
	 *            the property name.
	 * @return the value of the <code>name</code> or its replacing property, or
	 *         null if no such property exists.
	 */
	public String getTrimmed(String name) {
		String value = get(name);

		if (null == value) {
			return null;
		} else {
			return value.trim();
		}
	}

	public void set(String name, String value) {
		props.setProperty(name, value);
	}

	/**
	 * Get the value of the <code>name</code> property as an <code>int</code>.
	 * 
	 * If no such property exists, the provided default value is returned, or if
	 * the specified value is not a valid <code>int</code>, then an error is
	 * thrown.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            default value.
	 * @throws NumberFormatException
	 *             when the value is invalid
	 * @return property value as an <code>int</code>, or
	 *         <code>defaultValue</code>.
	 */
	public int getInt(String name, int defaultValue) {
		String valueString = getTrimmed(name);
		if (valueString == null)
			return defaultValue;
		String hexString = getHexDigits(valueString);
		if (hexString != null) {
			return Integer.parseInt(hexString, 16);
		}
		return Integer.parseInt(valueString);
	}

	/**
	 * Set the value of the <code>name</code> property to an <code>int</code>.
	 * 
	 * @param name
	 *            property name.
	 * @param value
	 *            <code>int</code> value of the property.
	 */
	public void setInt(String name, int value) {
		set(name, Integer.toString(value));
	}

	/**
	 * Get the value of the <code>name</code> property as a <code>long</code>.
	 * If no such property exists, the provided default value is returned, or if
	 * the specified value is not a valid <code>long</code>, then an error is
	 * thrown.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            default value.
	 * @throws NumberFormatException
	 *             when the value is invalid
	 * @return property value as a <code>long</code>, or
	 *         <code>defaultValue</code>.
	 */
	public long getLong(String name, long defaultValue) {
		String valueString = getTrimmed(name);
		if (valueString == null)
			return defaultValue;
		String hexString = getHexDigits(valueString);
		if (hexString != null) {
			return Long.parseLong(hexString, 16);
		}
		return Long.parseLong(valueString);
	}

	/**
	 * Get the value of the <code>name</code> property as a <code>long</code> or
	 * human readable format. If no such property exists, the provided default
	 * value is returned, or if the specified value is not a valid
	 * <code>long</code> or human readable format, then an error is thrown. You
	 * can use the following suffix (case insensitive): k(kilo), m(mega),
	 * g(giga), t(tera), p(peta), e(exa)
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            default value.
	 * @throws NumberFormatException
	 *             when the value is invalid
	 * @return property value as a <code>long</code>, or
	 *         <code>defaultValue</code>.
	 */
	public long getLongBytes(String name, long defaultValue) {
		String valueString = getTrimmed(name);
		if (valueString == null)
			return defaultValue;
		return StringUtils.TraditionalBinaryPrefix.string2long(valueString);
	}

	private String getHexDigits(String value) {
		boolean negative = false;
		String str = value;
		String hexString = null;
		if (value.startsWith("-")) {
			negative = true;
			str = value.substring(1);
		}
		if (str.startsWith("0x") || str.startsWith("0X")) {
			hexString = str.substring(2);
			if (negative) {
				hexString = "-" + hexString;
			}
			return hexString;
		}
		return null;
	}

	/**
	 * Set the value of the <code>name</code> property to a <code>long</code>.
	 * 
	 * @param name
	 *            property name.
	 * @param value
	 *            <code>long</code> value of the property.
	 */
	public void setLong(String name, long value) {
		set(name, Long.toString(value));
	}

	/**
	 * Get the value of the <code>name</code> property as a <code>float</code>.
	 * If no such property exists, the provided default value is returned, or if
	 * the specified value is not a valid <code>float</code>, then an error is
	 * thrown.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            default value.
	 * @throws NumberFormatException
	 *             when the value is invalid
	 * @return property value as a <code>float</code>, or
	 *         <code>defaultValue</code>.
	 */
	public float getFloat(String name, float defaultValue) {
		String valueString = getTrimmed(name);
		if (valueString == null)
			return defaultValue;
		return Float.parseFloat(valueString);
	}

	/**
	 * Set the value of the <code>name</code> property to a <code>float</code>.
	 * 
	 * @param name
	 *            property name.
	 * @param value
	 *            property value.
	 */
	public void setFloat(String name, float value) {
		set(name, Float.toString(value));
	}

	/**
	 * Get the value of the <code>name</code> property as a <code>boolean</code>
	 * . If no such property is specified, or if the specified value is not a
	 * valid <code>boolean</code>, then <code>defaultValue</code> is returned.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            default value.
	 * @return property value as a <code>boolean</code>, or
	 *         <code>defaultValue</code>.
	 */
	public boolean getBoolean(String name, boolean defaultValue) {
		String valueString = getTrimmed(name);
		if (null == valueString || "".equals(valueString)) {
			return defaultValue;
		}

		valueString = valueString.toLowerCase();

		if ("true".equals(valueString))
			return true;
		else if ("false".equals(valueString))
			return false;
		else
			return defaultValue;
	}

	/**
	 * Set the value of the <code>name</code> property to a <code>boolean</code>
	 * .
	 * 
	 * @param name
	 *            property name.
	 * @param value
	 *            <code>boolean</code> value of the property.
	 */
	public void setBoolean(String name, boolean value) {
		set(name, Boolean.toString(value));
	}

	/**
	 * Set the given property, if it is currently unset.
	 * 
	 * @param name
	 *            property name
	 * @param value
	 *            new value
	 */
	public void setBooleanIfUnset(String name, boolean value) {
		setIfUnset(name, Boolean.toString(value));
	}

	/**
	 * Sets a property if it is currently unset.
	 * 
	 * @param name
	 *            the property name
	 * @param value
	 *            the new value
	 */
	public synchronized void setIfUnset(String name, String value) {
		if (get(name) == null) {
			set(name, value);
		}
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as a
	 * collection of <code>String</code>s. If no such property is specified then
	 * empty collection is returned.
	 * <p>
	 * This is an optimized version of {@link #getStrings(String)}
	 * 
	 * @param name
	 *            property name.
	 * @return property value as a collection of <code>String</code>s.
	 */
	public Collection<String> getStringCollection(String name) {
		String valueString = get(name);
		return StringUtils.getStringCollection(valueString);
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as an
	 * array of <code>String</code>s. If no such property is specified then
	 * <code>null</code> is returned.
	 * 
	 * @param name
	 *            property name.
	 * @return property value as an array of <code>String</code>s, or
	 *         <code>null</code>.
	 */
	public String[] getStrings(String name) {
		String valueString = get(name);
		return StringUtils.getStrings(valueString);
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as an
	 * array of <code>String</code>s. If no such property is specified then
	 * default value is returned.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            The default value
	 * @return property value as an array of <code>String</code>s, or default
	 *         value.
	 */
	public String[] getStrings(String name, String... defaultValue) {
		String valueString = get(name);
		if (valueString == null) {
			return defaultValue;
		} else {
			return StringUtils.getStrings(valueString);
		}
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as a
	 * collection of <code>String</code>s, trimmed of the leading and trailing
	 * whitespace. If no such property is specified then empty
	 * <code>Collection</code> is returned.
	 * 
	 * @param name
	 *            property name.
	 * @return property value as a collection of <code>String</code>s, or empty
	 *         <code>Collection</code>
	 */
	public Collection<String> getTrimmedStringCollection(String name) {
		String valueString = get(name);
		if (null == valueString) {
			Collection<String> empty = new ArrayList<String>();
			return empty;
		}
		return StringUtils.getTrimmedStringCollection(valueString);
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as an
	 * array of <code>String</code>s, trimmed of the leading and trailing
	 * whitespace. If no such property is specified then an empty array is
	 * returned.
	 * 
	 * @param name
	 *            property name.
	 * @return property value as an array of trimmed <code>String</code>s, or
	 *         empty array.
	 */
	public String[] getTrimmedStrings(String name) {
		String valueString = get(name);
		return StringUtils.getTrimmedStrings(valueString);
	}

	/**
	 * Get the comma delimited values of the <code>name</code> property as an
	 * array of <code>String</code>s, trimmed of the leading and trailing
	 * whitespace. If no such property is specified then default value is
	 * returned.
	 * 
	 * @param name
	 *            property name.
	 * @param defaultValue
	 *            The default value
	 * @return property value as an array of trimmed <code>String</code>s, or
	 *         default value.
	 */
	public String[] getTrimmedStrings(String name, String... defaultValue) {
		String valueString = get(name);
		if (null == valueString) {
			return defaultValue;
		} else {
			return StringUtils.getTrimmedStrings(valueString);
		}
	}

	/**
	 * Set the array of string values for the <code>name</code> property as as
	 * comma delimited values.
	 * 
	 * @param name
	 *            property name.
	 * @param values
	 *            The values
	 */
	public void setStrings(String name, String... values) {
		set(name, StringUtils.arrayToString(values));
	}
}
