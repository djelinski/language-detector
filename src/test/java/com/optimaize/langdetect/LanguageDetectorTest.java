package com.optimaize.langdetect;

import com.google.common.base.Optional;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by Administrator on 2017-07-26.
 */
public class LanguageDetectorTest {
    private static LanguageDetector languageDetector;
    private static TextObjectFactory textObjectFactory;

    @BeforeClass
    public static void init() throws IOException {
        List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();
        LanguageProfileReader.removeForeignScripts(languageProfiles);

        languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();

        textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();
    }
    @Test
    public void detectJapanese() throws Exception {
        //query:
        TextObject textObject = textObjectFactory.forText("印刷用のトナーにおいては近赤外線を照射すると消色(無色化)する消色トナーが知られており、この消色トナーを用いて印刷を行う各種の画像処理装置が提案されている.");

        Optional<LdLocale> lang = languageDetector.detect(textObject);
        assertTrue("Failed to detect language", lang.isPresent());
        assertEquals("Detected language is not Japanese", lang.get().getLanguage(), "ja");
    }
    @Test
    public void detectShortJapanese() throws Exception {
        //query:
        TextObject textObject = textObjectFactory.forText("申請時間外のPCを強制的にシャットダウン。残業適正化とコスト削減");

        Optional<LdLocale> lang = languageDetector.detect(textObject);
        assertTrue("Failed to detect language", lang.isPresent());
        assertEquals("Detected language is not Japanese", "ja", lang.get().getLanguage());
    }
}