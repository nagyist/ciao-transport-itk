package uk.nhs.ciao.transport.spine.multipart;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import uk.nhs.ciao.util.SimpleEntry;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Represents a series of name/value entries
 * <p>
 * Multiple entries for the same name are permitted, and names are case-insensitive.
 */
public final class EntrySeries implements Iterable<Entry<String, String>> {
	private List<Entry<String, String>> entries;

	public EntrySeries() {
		this.entries = Lists.newArrayList();
	}
	
	public EntrySeries(final Iterable<? extends Entry<? extends String, ? extends String>> entries) {
		this();
		addAll(entries);
	}
	
	@Override
	public Iterator<Entry<String, String>> iterator() {
		return entries.iterator();
	}
	
	public Set<String> getNames() {
		final Set<String> names = Sets.newLinkedHashSet();
		final Set<String> lowercaseNames = Sets.newHashSet();
		
		for (final Entry<String, String> entry: entries) {
			if (!lowercaseNames.contains(entry.getKey().toLowerCase())) {
				names.add(entry.getKey());
			}
		}
		
		return names;
	}
	
	public String getFirstValue(final String name) {
		final String defaultValue = null;
		return getFirstValue(name, defaultValue);
	}
	
	public String getFirstValue(final String name, final String defaultValue) {
		for (final Entry<String, String> entry: entries) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}
		
		return defaultValue;
	}
	
	public List<String> getValues(final String name) {
		final List<String> values = Lists.newArrayList();
		
		for (final Entry<String, String> entry: entries) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				values.add(entry.getValue());
			}
		}
		
		return values;
	}
	
	public boolean containsName(final String name) {
		return getFirstValue(name) != null;
	}
	
	public void clear() {
		entries.clear();
	}
	
	public boolean remove(final String name) {
		boolean removed = true;
		
		for (final Iterator<Entry<String, String>> iterator = entries.iterator(); iterator.hasNext();) {
			if (iterator.next().getKey().equalsIgnoreCase(name)) {
				iterator.remove();
				removed = true;
			}
		}
		
		return removed;
	}
	
	public boolean remove(final String name, final String value) {		
		boolean removed = true;
		
		final SimpleEntry<String, String> entry = SimpleEntry.valueOf(name, value);
		for (final Iterator<Entry<String, String>> iterator = entries.iterator(); iterator.hasNext();) {
			if (entry.equals(iterator.next())) {
				iterator.remove();
				removed = true;
			}
		}
		
		return removed;
	}
	
	public void addAll(final Iterable<? extends Entry<? extends String, ? extends String>> entries) {
		if (entries == null) {
			return;
		}
		
		for (final Entry<? extends String, ? extends String> entry: entries) {
			add(entry.getKey(), entry.getValue());
		}
	}
	
	public void add(final String name, final String value) {
		Preconditions.checkNotNull(name, "name");
		Preconditions.checkNotNull(value, "value");
		
		final Entry<String, String> entry = SimpleEntry.valueOf(name, value);
		entries.add(entry);
	}
	
	public void set(final String name, final String value) {
		Preconditions.checkNotNull(name, "name");
		Preconditions.checkNotNull(value, "value");
		
		boolean isSet = false;
		for (final Iterator<Entry<String, String>> iterator = entries.iterator(); iterator.hasNext();) {
			final Entry<String, String> entry = iterator.next();
			if (entry.getKey().equalsIgnoreCase(name)) {
				if (isSet) {
					// was previously updated - remove the extra entry
					iterator.remove();
				} else {
					entry.setValue(value);
					isSet = true;
				}
			}
		}
		
		if (!isSet) {
			add(name, value);
		}
	}
	
	public void setOrRemove(final String name, final String value) {
		if (value == null) {
			remove(name);
		} else {
			set(name, value);
		}
	}
	
	@Override
	public String toString() {
		return entries.toString();
	}
	
	@Override
	public boolean equals(final Object obj) {
		if (obj == this) {
			return true;
		} else if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		
		final EntrySeries other = (EntrySeries)obj;
		return entries.equals(other.entries);
	}
	
	@Override
	public int hashCode() {
		return entries.hashCode();
	}
}
