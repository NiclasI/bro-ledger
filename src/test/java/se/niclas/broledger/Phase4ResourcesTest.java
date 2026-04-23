package se.niclas.broledger;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Phase 4 resource files and service changes — no JavaFX runtime required.
 */
class Phase4ResourcesTest {

    // ---- Stat icon assets --------------------------------------------------

    @Test
    void healthIconExistsOnClasspath() {
        assertAssetPresent("icon-health.png");
    }

    @Test
    void resolveIconExistsOnClasspath() {
        assertAssetPresent("icon-resolve.png");
    }

    @Test
    void fatigueIconExistsOnClasspath() {
        assertAssetPresent("icon-fatigue.png");
    }

    @Test
    void initiativeIconExistsOnClasspath() {
        assertAssetPresent("icon-initiative.png");
    }

    @Test
    void mSkillIconExistsOnClasspath() {
        assertAssetPresent("icon-mSkill.png");
    }

    @Test
    void rSkillIconExistsOnClasspath() {
        assertAssetPresent("icon-rSkill.png");
    }

    @Test
    void mDefenseIconExistsOnClasspath() {
        assertAssetPresent("icon-mDefense.png");
    }

    @Test
    void rDefenseIconExistsOnClasspath() {
        assertAssetPresent("icon-rDefense.png");
    }

    @Test
    void unknownTraitImageExistsOnClasspath() {
        assertAssetPresent("unknown-trait.png");
    }

    @Test
    void cinzelRegularFontExistsOnClasspath() {
        assertAssetPresent("Cinzel-Regular.otf");
    }

    // ---- brother-card.fxml -------------------------------------------------

    @Test
    void brotherCardFxmlExistsOnClasspath() {
        assertNotNull(
            App.class.getResource("/se/niclas/broledger/fxml/brother-card.fxml"),
            "brother-card.fxml not found");
    }

    @Test
    void brotherCardFxmlIsWellFormedXml() throws Exception {
        assertNotNull(parseCardFxml());
    }

    @Test
    void brotherCardRootIsVBox() throws Exception {
        assertEquals("VBox", parseCardFxml().getDocumentElement().getLocalName());
    }

    @Test
    void brotherCardControllerAttributeCorrect() throws Exception {
        String ctrl = parseCardFxml().getDocumentElement().getAttribute("fx:controller");
        assertEquals("se.niclas.broledger.ui.BrotherCardController", ctrl);
    }

    @Test
    void brotherCardControllerClassExists() throws Exception {
        String ctrl = parseCardFxml().getDocumentElement().getAttribute("fx:controller");
        assertDoesNotThrow(() -> Class.forName(ctrl), "BrotherCardController class not found");
    }

    @Test
    void brotherCardDeclaresRequiredFxIds() throws Exception {
        Document doc = parseCardFxml();
        List<String> required = Arrays.asList(
            "portraitView", "nameLabel", "titleLabel", "backgroundLabel",
            "traitsPane", "roleCombo", "statsGrid",
            "perksPane", "equipmentPane",
            "armorLabel", "fatigueLabel", "damageLabel"
        );
        for (String id : required) {
            assertTrue(hasFxId(doc, id), "brother-card.fxml missing fx:id=\"" + id + "\"");
        }
    }

    // ---- DictionaryService.getAllByType ------------------------------------

    @Test
    void getAllByTypeReturnsFiftyPerks() throws Exception {
        se.niclas.broledger.service.DictionaryService.getInstance().loadFromClasspath();
        List<?> perks = se.niclas.broledger.service.DictionaryService.getInstance()
                .getAllByType("perk");
        assertEquals(50, perks.size(), "Expected 50 perk entries in dictionary");
    }

    @Test
    void getAllByTypeIsSortedByName() throws Exception {
        se.niclas.broledger.service.DictionaryService.getInstance().loadFromClasspath();
        var perks = se.niclas.broledger.service.DictionaryService.getInstance()
                .getAllByType("perk");
        for (int i = 1; i < perks.size(); i++) {
            String prev = perks.get(i - 1).getValue().name;
            String curr = perks.get(i).getValue().name;
            assertTrue(prev.compareToIgnoreCase(curr) <= 0,
                "Perks not sorted: \"" + prev + "\" > \"" + curr + "\"");
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static void assertAssetPresent(String filename) {
        assertNotNull(
            App.class.getResource("/se/niclas/broledger/assets/" + filename),
            filename + " not found in classpath assets");
    }

    private static Document parseCardFxml() throws Exception {
        try (InputStream is = App.class.getResourceAsStream(
                "/se/niclas/broledger/fxml/brother-card.fxml")) {
            assertNotNull(is, "brother-card.fxml not found");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(is);
        }
    }

    private static boolean hasFxId(Document doc, String id) {
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            if (id.equals(el.getAttribute("fx:id"))
                    || id.equals(el.getAttributeNS("http://javafx.com/fxml", "id"))) {
                return true;
            }
        }
        return false;
    }
}
