/*

  Java API for WordNet Searching 1.0
  Copyright (c) 2007 by Brett Spell.

  This software is being provided to you, the LICENSEE, by under the following
  license.  By obtaining, using and/or copying this software, you agree that
  you have read, understood, and will comply with these terms and conditions:
   
  Permission to use, copy, modify and distribute this software and its
  documentation for any purpose and without fee or royalty is hereby granted,
  provided that you agree to comply with the following copyright notice and
  statements, including the disclaimer, and that the same appear on ALL copies
  of the software, database and documentation, including modifications that you
  make for internal use or for distribution.

  THIS SOFTWARE AND DATABASE IS PROVIDED "AS IS" WITHOUT REPRESENTATIONS OR
  WARRANTIES, EXPRESS OR IMPLIED.  BY WAY OF EXAMPLE, BUT NOT LIMITATION,  
  LICENSOR MAKES NO REPRESENTATIONS OR WARRANTIES OF MERCHANTABILITY OR FITNESS
  FOR ANY PARTICULAR PURPOSE OR THAT THE USE OF THE LICENSED SOFTWARE OR
  DOCUMENTATION WILL NOT INFRINGE ANY THIRD PARTY PATENTS, COPYRIGHTS,
  TRADEMARKS OR OTHER RIGHTS.

 */
package edu.smu.tspell.wordnet.impl.file;

import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetException;

import java.io.IOException;

import java.lang.ref.WeakReference;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;

/**
 * Provides a central location from which synset instances can be retrieved.
 * <br><p>
 * Synsets may be cached to improve performance and minimize memory usage
 * and when a synset is requested, the cache is first checked and the cached
 * instance will be returned if one exists. If the synset isn't found in the
 * cache, however, it will be read from disk and possibly added to the cache.
 * 
 * @author Brett Spell
 * @see <a href="http://java.sun.com/developer/technicalArticles/ALT/RefObj/">
 *      Reference Objects and Garbage Collection</a>
 */
public class SynsetFactory
{

	/**
	 * Default number of synsets to cache.
	 */
	private final static int DEFAULT_CACHE_SIZE = 500;

	/**
	 * Number of synsets that will be cached.
	 */
	private static int cacheSize = DEFAULT_CACHE_SIZE;

	/**
	 * Singleton instance of this class.
	 */
	private static SynsetFactory instance;

	/**
	 * Maps pointers to their corresponding synsets.
	 * <br><p>
	 * For each entry in the map, the key is an instance of
	 * {@link SynsetPointer} and the corresponding value is an instance of
	 * {@link Synset}.
	 * 
	 * @see #synsetPointers
	 * @see #addToCache(Synset, SynsetPointer)
	 * @see #getCachedSynset(SynsetPointer)
	 */
	private Map pointerSynsets = new WeakHashMap();

	/**
	 * Maps synsets to their corresponding pointers.
	 * <br><p>
	 * For each entry in the map, the key is an instance of {@link Synset}
	 * and the corresponding value is an instance of {@link SynsetPointer}.
	 * 
	 * @see #pointerSynsets
	 * @see #addToCache(Synset, SynsetPointer)
	 * @see #getCachedSynset(SynsetPointer)
	 */
	private Map synsetPointers = new WeakHashMap();

	/**
	 * Maintains "strong" references to the synsets to ensure that they
	 * don't get garbage collected.
	 */
	private LeastRecentlyUsedCache cache =
			new LeastRecentlyUsedCache(cacheSize);

	/**
	 * Static initializer that updates the cache size if one was specified.
	 */
	static
	{
		String propertyName = PropertyNames.SYNSET_CACHE_SIZE;
		String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null)
		{
			try
			{
				cacheSize = Integer.parseInt(propertyValue);
			}
			catch (NumberFormatException nfe)
			{
				System.err.println("'" + propertyValue + "' is an invalid " +
						"value for " + propertyName + " and will be ignored.");
			}
		}
	}

	/**
	 * Returns a reference to the singleton instance of this class.
	 * 
	 * @return Reference to the singleton instance of this class.
	 */
	public synchronized static SynsetFactory getInstance()
	{
		if (instance == null)
		{
			instance = new SynsetFactory();
		}
		return instance;
	}

	/**
	 * This constructor ensures that instances of this class can't be
	 * constructed by other classes.
	 * 
	 * @throws RetrievalException An error occurred reading the frame text file.
	 */
	private SynsetFactory()
	{
	}

        /** Returns an iterator over all synsets.
         * 
         * @return an iterator over all synsets
         */
        public synchronized Iterator<Synset> iterator() {
          return new SynsetIterator();
        }
        
        /** Provides a synset iterator. */
        class SynsetIterator implements Iterator<Synset> {

          /** the synset type */
          private SynsetType synsetType = SynsetType.NOUN;
          /** the next element */
          private Synset nextSynset;
          /** the synset reader */
          private SynsetReader synsetReader;
          
          /** Constructs a new SynsetIterator instance. */
          public SynsetIterator() {
            initializeSynsetReader();
            nextSynset = obtainNextSynset();
          }
          
          /** Returns <tt>true</tt> if the iteration has more elements. (In other
           * words, returns <tt>true</tt> if <tt>next</tt> would return an element
           * rather than throwing an exception.)
           *
           * @return <tt>true</tt> if the iterator has more elements.
           */
          public boolean hasNext() {
            return nextSynset != null;
          }

          /** Returns the next element in the iteration.
           *
           * @return the next element in the iteration.
           * @exception NoSuchElementException iteration has no more elements.
           */
          public Synset next() {
            if (nextSynset == null) {
              throw new NoSuchElementException();
            }
            final Synset synset = nextSynset;
            nextSynset = obtainNextSynset();
            return synset;
          }

          /** Obtains the next synset.
           * 
           * @return the next synset, or null when done
           */
          private Synset obtainNextSynset() {
            String synsetString;
            try {
              synsetString = synsetReader.readToNextEndOfLine();
              if (synsetString.isEmpty()) {
                if (synsetType.equals(SynsetType.NOUN)) {
                  synsetType = SynsetType.VERB;
                } else if (synsetType.equals(SynsetType.VERB)) {
                  synsetType = SynsetType.ADJECTIVE;
                } else if (synsetType.equals(SynsetType.ADJECTIVE)) {
                  synsetType = SynsetType.ADVERB;
                } else if (synsetType.equals(SynsetType.ADVERB)) {
                  return null;
                }
                initializeSynsetReader();
                synsetString = synsetReader.readToNextEndOfLine();
                assert !synsetString.isEmpty();
              }
            } catch (final IOException ex) {
              throw new RetrievalException("An error occurred reading the synset data", ex);
            }
            
            final SynsetParser parser = new SynsetParser();
            return parser.createSynset(synsetString);
          }
          
          /** Initializes the synset reader by positioning the file pointer past the license text. */
          private void initializeSynsetReader() {
            synsetReader = SynsetReader.getInstance(synsetType);
            try {
              synsetReader.seek(1740L);
            } catch (final IOException ex) {
              throw new RetrievalException("An error occurred seeking the initial synset data for " + synsetType, ex);
            }
          }
          
          /** Removes from the underlying collection the last element returned by the
           * iterator (optional operation).  This method can be called only once per
           * call to <tt>next</tt>.  The behavior of an iterator is unspecified if
           * the underlying collection is modified while the iteration is in
           * progress in any way other than by calling this method.
           *
           * @exception UnsupportedOperationException if the <tt>remove</tt>
           *		  operation is not supported by this Iterator.

           * @exception IllegalStateException if the <tt>next</tt> method has not
           *		  yet been called, or the <tt>remove</tt> method has already
           *		  been called after the last call to the <tt>next</tt>
           *		  method.
           */
          public void remove() {
            throw new UnsupportedOperationException("Not supported.");
          }
        }
        
	/**
	 * Returns a synset that's referenced by a pointer, reading it from disk
	 * if necessary.
	 * 
	 * @param  pointer Pointer that identifies the location of the synset in
	 *         the database.
	 * @return Synset that was read from the database either as a result of
	 *         this call or a previous one that resulted in it being cached.
	 * @throws WordNetException An error occurred reading or parsing the
	 *         synset.
	 */
	public synchronized Synset getSynset(SynsetPointer pointer)
			throws WordNetException
	{
		Synset synset = getCachedSynset(pointer);
		if (synset == null)
		{
			synset = readSynset(pointer);
			addToCache(synset, pointer);
			cache.put(pointer, synset);
		}
		return synset;
	}

	/**
	 * Attempts to return a synset that was previously stored in the cache.
	 * 
	 * @param  pointer Identifies the database location associated with the
	 *         synset.
	 * @return Previously cached synset object or <code>null</code> if it
	 *         is not currently stored in the cache.
	 * @see    #addToCache(Synset, SynsetPointer)
	 */
	private Synset getCachedSynset(SynsetPointer pointer)
	{
		WeakReference weakRef = (WeakReference)(pointerSynsets.get(pointer));
		return (weakRef != null ? (Synset)(weakRef.get()) : null);
	}

	/**
	 * Adds a synset to the cache.
	 * <br><p>
	 * We add the synset to two different instances of {@link WeakHashMap}
	 * even though we only provide a lookup method using the one that has
	 * pointers for its keys. The reason for doing this is that it has a
	 * very desirable affect on caching and garbage collection. Specifically,
	 * it ensures that we'll be able to retrieve a synset from the cache as
	 * long as a normal (strong) reference exists to the synset. This works
	 * because the non-lookup map has a weak reference to the synset as its
	 * key and a strong reference to the pointer as the corresponding value.
	 * As long as a strong reference exists to that synset its map entry
	 * will remain intact, which in turn will cause the entry in the other
	 * map (the one that maps pointers to synsets) to remain valid as well
	 * due to the existance of the strong reference in the first map.
	 * 
	 * @param  synset Synset that's to be stored in the cache.
	 * @param  pointer Identifies the location from which the synset was loaded.
	 * @see    #getCachedSynset(SynsetPointer)
	 */
  @SuppressWarnings("unchecked")
	private void addToCache(Synset synset, SynsetPointer pointer)
	{
		pointerSynsets.put(pointer, new WeakReference(synset));
		synsetPointers.put(synset, pointer);
	}

	/**
	 * Reads and returns a synset from the WordNet database.
	 * 
	 * @param  pointer Identifies the location from which to read the synset.
	 * @return Newly created synset instance.
	 * @throws RetrievalException An error occurred reading the data.
	 * @throws ParseException An error occurred parsing the data.
	 */
	private Synset readSynset(SynsetPointer pointer)
			throws RetrievalException, ParseException
	{
		Synset synset;
		String data = null;
		try
		{
			SynsetReader reader = SynsetReader.getInstance(
					pointer.getType());
			data = reader.readData(pointer);
			SynsetParser parser = new SynsetParser();
			synset = parser.createSynset(data);
		}
		catch (ParseException pe)
		{
			throw pe;
		}
		catch (IOException ioe)
		{
			throw new RetrievalException(
					"An error occurred reading the synset data", ioe);
		}
		catch (Exception e)
		{
			throw new ParseException(
					"An error occurred parsing the synset data: " + data, e);
		}
		return synset;
	}

}