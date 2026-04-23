package se.niclas.broledger;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies Phase 3 resource files (FXML, CSS) are present, structurally valid,
 * and reference the correct controller and field IDs — without requiring a
 * JavaFX runtime.
 */
class Phase3ResourcesTest {

    // ---- CSS ---------------------------------------------------------------

    @Test
    void keeperCssExistsOnClasspath() {
        assertNotNull(
            App.class.getResource("/se/niclas/broledger/css/keeper.css"),
            "keeper.css not found in classpath");
    }

    @Test
    void keeperCssIsNonEmpty() throws Exception {
        try (InputStream is = App.class.getResourceAsStream(
                "/se/niclas/broledger/css/keeper.css")) {
            assertNotNull(is);
            assertTrue(is.readAllBytes().length > 0, "keeper.css is empty");
        }
    }

    // ---- FXML exists and parses as XML -------------------------------------

    @Test
    void mainFxmlExistsOnClasspath() {
        assertNotNull(
            App.class.getResource("/se/niclas/broledger/fxml/main.fxml"),
            "main.fxml not found in classpath");
    }

    @Test
    void mainFxmlIsWellFormedXml() throws Exception {
        Document doc = parseMainFxml();
        assertNotNull(doc, "Could not parse main.fxml as XML");
    }

    // ---- Root element ------------------------------------------------------

    @Test
    void mainFxmlRootIsBorderPane() throws Exception {
        Element root = parseMainFxml().getDocumentElement();
        assertEquals("BorderPane", root.getLocalName());
    }

    @Test
    void mainFxmlControllerAttributeCorrect() throws Exception {
        Element root = parseMainFxml().getDocumentElement();
        String controller = root.getAttribute("fx:controller");
        assertEquals("se.niclas.broledger.ui.MainController", controller);
    }

    @Test
    void controllerClassExists() throws Exception {
        String fxController = parseMainFxml().getDocumentElement()
                .getAttribute("fx:controller");
        assertDoesNotThrow(() -> Class.forName(fxController),
                "Controller class not found: " + fxController);
    }

    // ---- Required fx:id fields are declared --------------------------------

    @Test
    void fxmlDeclaresToolbarPane() throws Exception {
        assertTrue(hasFxId(parseMainFxml(), "toolbarPane"),
                "main.fxml must declare fx:id=\"toolbarPane\"");
    }

    @Test
    void fxmlDeclaresCenterPane() throws Exception {
        assertTrue(hasFxId(parseMainFxml(), "centerPane"),
                "main.fxml must declare fx:id=\"centerPane\"");
    }

    @Test
    void fxmlDeclaresStatusLabel() throws Exception {
        assertTrue(hasFxId(parseMainFxml(), "statusLabel"),
                "main.fxml must declare fx:id=\"statusLabel\"");
    }

    // ---- Open Save action exists on controller -----------------------------

    @Test
    void mainControllerHasOpenSaveMethod() throws Exception {
        String fxController = parseMainFxml().getDocumentElement()
                .getAttribute("fx:controller");
        Class<?> ctrl = Class.forName(fxController);
        boolean found = java.util.Arrays.stream(ctrl.getDeclaredMethods())
                .anyMatch(m -> "openSave".equals(m.getName()));
        assertTrue(found, "MainController must declare an openSave() method");
    }

    // ---- Helpers -----------------------------------------------------------

    private static Document parseMainFxml() throws Exception {
        try (InputStream is = App.class.getResourceAsStream(
                "/se/niclas/broledger/fxml/main.fxml")) {
            assertNotNull(is, "main.fxml not found");
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(is);
        }
    }

    /** Returns true if any element in the document has the given fx:id value. */
    private static boolean hasFxId(Document doc, String id) {
        // fx:id uses the JavaFX namespace — search both with and without NS
        NodeList all = doc.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            // FXML uses the fx namespace; attribute may appear as "fx:id" or just "id"
            if (id.equals(el.getAttribute("fx:id"))
                    || id.equals(el.getAttributeNS("http://javafx.com/fxml", "id"))) {
                return true;
            }
        }
        return false;
    }
}
