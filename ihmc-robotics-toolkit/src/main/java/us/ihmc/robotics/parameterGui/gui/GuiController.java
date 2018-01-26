package us.ihmc.robotics.parameterGui.gui;

import java.util.HashMap;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import us.ihmc.commons.PrintTools;
import us.ihmc.robotics.parameterGui.ChangeCollector;
import us.ihmc.robotics.parameterGui.GuiParameter;
import us.ihmc.robotics.parameterGui.GuiRegistry;
import us.ihmc.robotics.parameterGui.tree.ParameterTree;
import us.ihmc.robotics.parameterGui.tree.ParameterTreeParameter;
import us.ihmc.robotics.parameterGui.tree.ParameterTreeValue;
import us.ihmc.robotics.parameterGui.tuning.TuningBoxManager;

public class GuiController
{
   @FXML
   private TextField searchFieldParameters;
   @FXML
   private TextField searchFieldNamespaces;
   @FXML
   private CheckBox hideNamespaces;
   @FXML
   private ParameterTree tree;
   @FXML
   private VBox tuningBox;
   @FXML
   private StackPane inputPane;

   private final HashMap<String, GuiParameter> parameterMap = new HashMap<>();
   private ChangeCollector changeCollector;
   private TuningBoxManager tuningBoxManager;

   public void initialize()
   {
      searchFieldParameters.textProperty().addListener(observable -> updateTree());
      searchFieldNamespaces.textProperty().addListener(observable -> updateTree());
      tuningBoxManager = new TuningBoxManager(tuningBox);

      tree.setOnMouseClicked(new EventHandler<MouseEvent>()
      {
         @Override
         public void handle(MouseEvent mouseEvent)
         {
            TreeItem<ParameterTreeValue> selectedItem = tree.getSelectionModel().getSelectedItem();
            if (selectedItem == null || selectedItem.getValue().isRegistry() || mouseEvent.getClickCount() < 2)
            {
               return;
            }
            GuiParameter parameter = ((ParameterTreeParameter) selectedItem.getValue()).getParameter();
            tuningBoxManager.handleNewParameter(parameter);
         }
      });
      tree.setOnKeyPressed(new EventHandler<KeyEvent>()
      {
         @Override
         public void handle(KeyEvent event)
         {
            TreeItem<ParameterTreeValue> selectedItem = tree.getSelectionModel().getSelectedItem();
            if (selectedItem == null || selectedItem.getValue().isRegistry() || event.getCode() != KeyCode.ENTER)
            {
               return;
            }
            GuiParameter parameter = ((ParameterTreeParameter) selectedItem.getValue()).getParameter();
            tuningBoxManager.handleNewParameter(parameter);
         }
      });
   }

   @FXML
   protected void handleNamespaceButton(ActionEvent event)
   {
      updateTree();
      searchFieldNamespaces.setDisable(hideNamespaces.isSelected());
   }

   private void updateTree()
   {
      tree.filterRegistries(hideNamespaces.isSelected(), searchFieldParameters.getText(), searchFieldNamespaces.getText());
   }

   public void addInputNode(Node node)
   {
      if (node != null)
      {
         inputPane.getChildren().add(node);
      }
   }

   public void setRegistry(GuiRegistry fullRegistry)
   {
      tree.setRegistries(fullRegistry);
      updateTree();
      tuningBoxManager.clearAllParameters();

      changeCollector = new ChangeCollector();
      parameterMap.clear();
      List<GuiParameter> allParameters = fullRegistry.getAllParameters();
      allParameters.stream().forEach(parameter -> {
         parameter.addChangedListener(changeCollector);
         parameterMap.put(parameter.getUniqueName(), parameter);
      });
   }

   public List<GuiParameter> pollChangedParameters()
   {
      if (changeCollector == null)
      {
         return null;
      }

      return changeCollector.getChangedParametersAndClear();
   }

   public void updateParameters(List<GuiParameter> externallyChangesParameters)
   {
      if (changeCollector == null)
      {
         return;
      }

      changeCollector.stopRecording();
      externallyChangesParameters.stream().forEach(externalParameter -> {
         GuiParameter localParameter = parameterMap.get(externalParameter.getUniqueName());
         if (localParameter == null)
         {
            PrintTools.warn("Did not find " + externalParameter.getName() + " skipping...");
         }
         else
         {
            localParameter.setValue(externalParameter.getCurrentValue());
         }
      });
      changeCollector.startRecording();
   }
}
