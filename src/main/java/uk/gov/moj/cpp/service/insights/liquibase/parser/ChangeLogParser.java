package uk.gov.moj.cpp.service.insights.liquibase.parser;

import uk.gov.moj.cpp.service.insights.liquibase.exception.ChangeLogParsingException;
import uk.gov.moj.cpp.service.insights.liquibase.model.Table;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.AddColumnChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.AddForeignKeyConstraintChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.AddPrimaryKeyChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.Change;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.CreateIndexChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.CreateTableChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.DropColumnChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.DropIndexChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.DropTableChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.RenameColumnChange;
import uk.gov.moj.cpp.service.insights.liquibase.parser.changes.RenameTableChange;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ChangeLogParser implements IChangeLogParser {

    @Override
    public void parseChangeLog(File xmlFile, Map<String, Table> tables) throws ChangeLogParsingException {

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Handle namespaces
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            NodeList changeSetNodes = doc.getElementsByTagNameNS("*", "changeSet");
            for (int i = 0; i < changeSetNodes.getLength(); i++) {
                Element changeSetElement = (Element) changeSetNodes.item(i);
                NodeList changeNodes = changeSetElement.getChildNodes();
                for (int j = 0; j < changeNodes.getLength(); j++) {
                    Node changeNode = changeNodes.item(j);
                    if (changeNode instanceof Element changeElement) { // Pattern matching for instanceof
                        String changeType = changeElement.getLocalName();
                        Change change = switch (changeType) {
                            case "createTable" -> new CreateTableChange(changeElement);
                            case "addColumn" -> new AddColumnChange(changeElement);
                            case "addPrimaryKey" -> new AddPrimaryKeyChange(changeElement);
                            case "addForeignKeyConstraint" ->
                                    new AddForeignKeyConstraintChange(changeElement);
                            case "renameTable" -> new RenameTableChange(changeElement);
                            case "renameColumn" -> new RenameColumnChange(changeElement);
                            case "dropTable" -> new DropTableChange(changeElement);
                            case "createIndex" -> new CreateIndexChange(changeElement);
                            case "dropIndex" -> new DropIndexChange(changeElement);
                            case "dropColumn" -> new DropColumnChange(changeElement);
                            default -> {
                                yield null;
                            }
                        };
                        if (change != null) {
                            change.apply(tables);
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ChangeLogParsingException("Failed to parse change log: " + xmlFile.getName(), e);
        }
    }
}

