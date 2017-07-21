/*
 * Copyright 2011 Fabian Kessler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.optimaize.langdetect.profiles;

import com.optimaize.langdetect.frma.LangProfileReader;
import com.optimaize.langdetect.i18n.LdLocale;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

/**
 * Reads {@link LanguageProfile}s.
 *
 * @author Fabian Kessler
 */
public class LanguageProfileReader {

    private static final LangProfileReader internalReader = new LangProfileReader();
    private static final String PROFILES_DIR = "languages";


    /**
     * Reads a {@link LanguageProfile} from a File in UTF-8.
     */
    public LanguageProfile read(File profileFile) throws IOException {
        return OldLangProfileConverter.convert(internalReader.read(profileFile));
    }

    /**
     * Reads a {@link LanguageProfile} from an InputStream in UTF-8.
     */
    public LanguageProfile read(InputStream inputStream) throws IOException {
        return OldLangProfileConverter.convert(internalReader.read(inputStream));
    }


    /**
     * Load profiles from the classpath in a specific directory.
     *
     * <p>This is usually used to load built-in profiles, shipped with the jar.</p>
     *
     * @param classLoader the ClassLoader to load the profiles from. Use {@code MyClass.class.getClassLoader()}
     * @param profileDirectory profile directory path inside the classpath. The default profiles are in "languages".
     * @param profileFileNames for example ["en", "fr", "de"].
     */
    public List<LanguageProfile> read(ClassLoader classLoader, String profileDirectory, Collection<String> profileFileNames) throws IOException {
        List<LanguageProfile> loaded = new ArrayList<>(profileFileNames.size());
        for (String profileFileName : profileFileNames) {
            String path = makePathForClassLoader(profileDirectory, profileFileName);
            try (InputStream in = classLoader.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IOException("No language file available named "+profileFileName+" at " + path + "!");
                }
                loaded.add( read(in) );
            }
        }
        return loaded;
    }

    private String makePathForClassLoader(String profileDirectory, String fileName) {
        //WITHOUT slash before the profileDirectory when using the classloader!
        return profileDirectory + '/' + fileName;
    }

    /**
     * Same as {@link #read(ClassLoader, String, java.util.Collection)} using the class loader of this class.
     */
    public List<LanguageProfile> read(String profileDirectory, Collection<String> profileFileNames) throws IOException {
        return read(LanguageProfileReader.class.getClassLoader(), profileDirectory, profileFileNames);
    }

    /**
     * Same as {@link #read(ClassLoader, String, java.util.Collection)} using the class loader of this class,
     * and the default profiles directory of this library.
     */
    public List<LanguageProfile> read(Collection<String> profileFileNames) throws IOException {
        return read(LanguageProfileReader.class.getClassLoader(), PROFILES_DIR, profileFileNames);
    }

    @NotNull
    public LanguageProfile readBuiltIn(@NotNull LdLocale locale) throws IOException {
        String filename = makeProfileFileName(locale);
        String path = makePathForClassLoader(PROFILES_DIR, filename);
        try (InputStream in = LanguageProfileReader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("No language file available named "+filename+" at " + path + "!");
            }
            return read(in);
        }
    }

    @NotNull
    private String makeProfileFileName(@NotNull LdLocale locale) {
        return locale.toString();
    }

    @NotNull
    public List<LanguageProfile> readBuiltIn(@NotNull Collection<LdLocale> languages) throws IOException {
        List<String> profileNames = new ArrayList<>();
        for (LdLocale locale : languages) {
            profileNames.add(makeProfileFileName(locale));
        }
        return read(LanguageProfileReader.class.getClassLoader(), PROFILES_DIR, profileNames);
    }

    /**
     * @deprecated renamed to readAllBuiltIn()
     */
    public List<LanguageProfile> readAll() throws IOException {
        return readAllBuiltIn();
    }
    /**
     * Reads all built-in language profiles from the "languages" folder (shipped with the jar).
     */
    public List<LanguageProfile> readAllBuiltIn() throws IOException {
        List<LanguageProfile> loaded = new ArrayList<>();
        for (LdLocale locale : BuiltInLanguages.getLanguages()) {
            loaded.add(readBuiltIn(locale));
        }
        return loaded;
    }

    /**
     * Loads all profiles from the specified directory.
     *
     * Do not use this method for files distributed within a jar.
     *
     * @param path profile directory path
     * @return empty if there is no language file in it.
     */
    public List<LanguageProfile> readAll(File path) throws IOException {
        if (!path.exists()) {
            throw new IOException("No such folder: "+path);
        }
        if (!path.canRead()) {
            throw new IOException("Folder not readable: "+path);
        }
        File[] listFiles = path.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return looksLikeLanguageProfileFile(pathname);
            }
        });
        if (listFiles == null) {
            throw new IOException("Failed reading from folder: " + path);
        }

        List<LanguageProfile> profiles = new ArrayList<>(listFiles.length);
        for (File file: listFiles) {
            if (!looksLikeLanguageProfileFile(file)) {
                continue;
            }
            profiles.add(read(file));
        }
        return profiles;
    }

    private boolean looksLikeLanguageProfileFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        return looksLikeLanguageProfileName(file.getName());
    }
    private boolean looksLikeLanguageProfileName(String fileName) {
        if (fileName.contains(".")) {
            return false;
        }
        try {
            LdLocale.fromString(fileName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Removes grams in foreign scripts from the language profile.
     *
     * This method finds the most popular script in the passed profile, and removes all ngrams using other scripts,
     * special-casing Japanese, which is written using HAN, HIRAGANA and KATAKANA.
     * @param profile profile to process
     * @return New profile with bad grams removed
     */
    public static LanguageProfile removeForeignScripts(LanguageProfile profile) {
        boolean isJapanese = "ja".equals(profile.getLocale().toString());
        Map<Character.UnicodeScript, Long> counts = new HashMap<>();

        for(Map.Entry<String,Integer> ngram: profile.iterateGrams()) {
            countByScript(counts, ngram.getKey());
        }
        Map.Entry<Character.UnicodeScript, Long> maxEntry = null;
        for (Map.Entry<Character.UnicodeScript, Long> countEntry : counts.entrySet()) {
            if(maxEntry == null || maxEntry.getValue() < countEntry.getValue())
                maxEntry = countEntry;
        }
        return copyProfile(profile, maxEntry.getKey(),isJapanese);
    }

    /**
     * Removes grams in foreign scripts from all languages on the passed list.
     *
     * This method finds the most popular script in the passed profile, and removes all ngrams using other scripts,
     * special-casing Japanese, which is written using HAN, HIRAGANA and KATAKANA.
      * @param profiles list of profiles to process
     */
    public static void removeForeignScripts(List<LanguageProfile> profiles) {
        ListIterator<LanguageProfile> profileListIterator = profiles.listIterator();
        while (profileListIterator.hasNext()) {
            LanguageProfile profile = profileListIterator.next();
            profileListIterator.set(removeForeignScripts(profile));
        }
    }

    private static LanguageProfile copyProfile(LanguageProfile profile, Character.UnicodeScript unicodeScript, boolean isJapanese) {
        List<Integer> gramLengths = profile.getGramLengths();
        Map<Integer, Map<String, Integer>> ngrams = new HashMap<>(gramLengths.size());
        for (Integer gramLength : gramLengths) {
            HashMap<String, Integer> currentGramMap = new HashMap<>();
            ngrams.put(gramLength,currentGramMap);
            for (Map.Entry<String, Integer> ngram : profile.iterateGrams(gramLength)) {
                Character.UnicodeScript script = getGramScript(ngram.getKey(), isJapanese);
                if(script == null || script == unicodeScript)
                    currentGramMap.put(ngram.getKey(),ngram.getValue());
            }
        }
        return new LanguageProfileImpl(profile.getLocale(),ngrams);
    }

    private static Character.UnicodeScript getGramScript(String text, boolean isJapanese) {
        Character.UnicodeScript last = null;
        for (int i=0; i<text.length(); i++) {
            char c = text.charAt(i);
            if(c == '�')
                return Character.UnicodeScript.UNKNOWN;
            Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(c);
            switch (unicodeScript) {
                case INHERITED:
                case COMMON:
                case UNKNOWN:
                    break;
                default:
                    if(isJapanese && (unicodeScript == Character.UnicodeScript.KATAKANA || unicodeScript == Character.UnicodeScript.HIRAGANA))
                        unicodeScript = Character.UnicodeScript.HAN;
                    if(last != null && last != unicodeScript)
                        throw new RuntimeException("Mixed scripts in ngram '"+text+"', found: "+last+" and "+unicodeScript);
                    last = unicodeScript;
            }
        }
        return last;
    }

    private static void countByScript(Map<Character.UnicodeScript, Long> counter, CharSequence text) {
        for (int i=0; i<text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeScript unicodeScript = Character.UnicodeScript.of(c);
            switch (unicodeScript) {
                case INHERITED:
                case COMMON:
                case UNKNOWN:
                    //don't count it
                    break;
                default:
                    increment(counter, unicodeScript);
            }
        }
    }

    private static void increment(Map<Character.UnicodeScript, Long> counter, Character.UnicodeScript unicodeScript) {
        Long number = counter.get(unicodeScript);
        if (number==null) {
            counter.put(unicodeScript, 1L);
        } else {
            counter.put(unicodeScript, number+1);
        }
    }

}
