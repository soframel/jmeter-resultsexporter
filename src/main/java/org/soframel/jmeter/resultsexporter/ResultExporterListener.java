package org.soframel.jmeter.resultsexporter;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.io.FileUtils;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.save.SaveGraphicsService;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.visualizers.Printable;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

/**
 * Exports result into a given directory. A pop-up is shown at the end of a test
 * asking if results should be saved. If yes, another pop-up asks for an input
 * directory. The CSV file is copied in this directory, as well as images of all TestListeners 
 * and of the ThreadGroup.  
 * 
 * @author soframel
 * 
 */
public class ResultExporterListener extends ResultCollector {

	public static String EXPORT_COMMAND = "export";
	
	private static final long serialVersionUID = -3408497844654656634L;

	private static final Logger log = LoggingManager.getLoggerForClass(); 
	
	public ResultExporterListener() {
	
	}

	@Override
	public void testStarted() {
		// delete main file in root dir for each test, because it is reused
		File f = new File(this.getFilename());
		f.delete();

		super.testStarted();
	}

	@Override
	public void testEnded() {
		super.testEnded();
		this.exportAll();
	}

	/**
	 * Export all the test results
	 */
	public void exportAll() {		
		log.debug("ResultExporterListener: exportAll called");
		log.debug("ResultExporter: name="+this.getName());

		// show popup to choose if you want to save your tests
		int result = JOptionPane.showConfirmDialog(null,
				"Do you want to export the results for this test?", "Export?",
				JOptionPane.YES_NO_OPTION);
		if (result == JOptionPane.YES_OPTION) {
			log.debug("ResultExporterListener: exporting");

			// Find root directory
			String filename=this.getFilename();			
			filename = FileServer.resolveBaseRelativeName(filename);
			
			log.debug("Filename after resolving=" + filename);
			File rootFile = new File(filename);
			File rootDir = rootFile.getParentFile();

			////Directory of test
			String inputValue = JOptionPane.showInputDialog("Name of the test");
			if (inputValue == null || inputValue.equals("")
					|| inputValue.trim().equals("")) {
				inputValue = "default";
			} else
				inputValue = inputValue.trim();
			File exportDir = new File(rootDir, inputValue);
			
			////if directory exists: ask if we continue
			if(exportDir.exists()){
				int delete = JOptionPane.showConfirmDialog(null,
						"Directory exists. Delete its content?", "Directory already exists",
						JOptionPane.YES_NO_OPTION);
				if(delete==JOptionPane.YES_OPTION){
					try {
						FileUtils.deleteDirectory(exportDir);
					} catch (IOException e) {
						log.error("IOException while deleting directory "+exportDir.getAbsolutePath(), e);
					}
				}
				else //do not continue
					return;
			}
			
			////create dir
			if (!exportDir.exists())
				exportDir.mkdirs();			

			//// copy main CSV/XML file
			File mainFile = new File(exportDir, rootFile.getName());
			try {
				FileUtils.copyFile(rootFile, mainFile);
			} catch (IOException e) {
				log.error("An IOException occured while copying main file: "+ e.getMessage(), e);
			}

			////Find other plugins and export images
			//extend window to take screenshots in fullscreen
			GuiPackage.getInstance().getMainFrame().setExtendedState(JFrame.MAXIMIZED_BOTH);

			//Find ThreadGroup from tree
			JTree tree=GuiPackage.getInstance().getMainFrame().getTree();	
			JMeterTreeNode tgNode=this.getThreadGroupTreeNode(tree);
		
			//open once every TestElement			
			this.showAllTestElements(exportDir, tree, tgNode);
			
			//export images		
			log.info("exporting nodes' images");
			this.exportAllTestElementsAsImages(exportDir, tgNode);
			
			////Confirmation of end of processing
			JOptionPane.showMessageDialog(null, "Export is finished", "Export", JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	/**
	 * Display once every TestElement
	 * @param folder
	 * @param tree
	 * @param node
	 */
	private void showAllTestElements(File folder, JTree tree, JMeterTreeNode node){
		if(node.getTestElement().isEnabled()){
			if(node.getTestElement() instanceof TestElement
					&& !node.getTestElement().getClass().equals(this.getClass())){ 	
					
				//select element
				TreeNode[] pathNodes=node.getPath();
				TreePath path = new TreePath(pathNodes);				
				tree.scrollPathToVisible(path);
				tree.setSelectionPath(path);
				
				//wait until component is shown
				JMeterGUIComponent component = GuiPackage.getInstance().getGui(node.getTestElement());
				JComponent jcomp=(JComponent) component;
				while(!jcomp.isShowing()){
					log.info("Waiting for component to show: "+node.getTestElement().getName());
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						log.error("InterruptedException while waiting in showAllTestElements: "+e.getMessage(), e);
					}
				}
			}
			
			//process children
			Enumeration<JMeterTreeNode> children=node.children();
			while(children.hasMoreElements()){
				JMeterTreeNode child=children.nextElement();
				this.showAllTestElements(folder, tree, child);
			}
		
		}
	}

	/**
	 * Export all TestElements as images
	 * @param folder
	 * @param node
	 */
	private void exportAllTestElementsAsImages(File folder, JMeterTreeNode node){
		if(node.getTestElement().isEnabled()){
		
			if(node.getTestElement() instanceof TestElement
					&& !node.getTestElement().getClass().equals(this.getClass())){			
				this.exportTestElementAsImage(folder, (TestElement)node.getTestElement());
			}
			
			//process children
			Enumeration<JMeterTreeNode> children=node.children();
			while(children.hasMoreElements()){
				JMeterTreeNode child=children.nextElement();
				this.exportAllTestElementsAsImages(folder, child);
			}
		
		}
	}

	/**
	 * Find the tree node from the current thread group (containing this ResultExporter)
	 * @param tree
	 * @return
	 */
	private JMeterTreeNode getThreadGroupTreeNode(JTree tree){		
		TreeModel model=tree.getModel();
		JMeterTreeNode root=(JMeterTreeNode) model.getRoot();
		
		JMeterTreeNode resultExporterNode=this.getResultExporterTreeNode(root);
		return this.getContainingThreadGroup(resultExporterNode);
	}
	
	/**
	 * Find the TreeNode for this ResultExporterListener
	 * @param node
	 * @return
	 */
	public JMeterTreeNode getResultExporterTreeNode(JMeterTreeNode node){
		JMeterTreeNode foundNode=null;		

		if(node.getTestElement() instanceof ResultExporterListener 
				&& node.isEnabled()
				&& node.getName().equals(this.getName())
				){
			log.debug("ResultExporter found");
			foundNode=node;	
		}

		if(foundNode==null){
			//search in children				
			Enumeration<JMeterTreeNode> children=node.children();
			while(children.hasMoreElements() && foundNode==null){
				JMeterTreeNode child=children.nextElement();
				foundNode=this.getResultExporterTreeNode(child);
			}			
		}
		return foundNode;
	}	
	
	/**
	 * Find the first ThreadGroup container of this node's TestElement
	 * @param node
	 * @return
	 */
	public JMeterTreeNode getContainingThreadGroup(JMeterTreeNode node){
		JMeterTreeNode containingTG=null;
		
		JMeterTreeNode parent=(JMeterTreeNode) node.getParent();
		if(parent.getTestElement() instanceof ThreadGroup)
			containingTG=parent;
		
		if(containingTG==null && parent!=null && !parent.equals(node)){
			containingTG=this.getContainingThreadGroup(parent);
		}
		
		return containingTG;
	}
	
	/**
	 * export a single TestElement as an image
	 * @param folder
	 * @param testEl
	 */
	private void exportTestElementAsImage(File folder, TestElement testEl) {
		JMeterGUIComponent component = GuiPackage.getInstance().getGui(testEl);
		if (component instanceof Printable) {
			JComponent comp = ((Printable) component).getPrintableComponent();				
			String name = testEl.getName();			
			
			component.configure(testEl);
			
			//change name to be a possible file name
			name=ResultExporterListener.transformNameIntoFilename(name);
			name=name+ SaveGraphicsService.PNG_EXTENSION;
			File f = new File(folder, name);				
			log.info("Saving file "+f.getAbsolutePath());			
			this.saveJComponent(f,SaveGraphicsService.PNG, comp);			
		}
	}

	/**
	 * parse and transforms slashes and other characters that are not authorized in file names
	 * @param name
	 * @return a filename (without extension)
	 */
	public static String transformNameIntoFilename(String name){
		String result=name;
		if(name!=null){		
			result=name.replace("\\", "_");
			result=result.replace("/", "_");
			result=result.replace(".", "_");
			result=result.replace("=", "_");
			if(result.length()>256)
				result=result.substring(0, 253);
		}
		
		if(result==null || result.equals(""))
			result="default";
		
		return result;
	}
	
	/**
	 * Saving of a JComponent
	 * @param f
	 * @param type
	 * @param component
	 */
	private void saveJComponent(File f, int type, JComponent component) {		
		GuiPackage.getInstance().getMainFrame().setMainPanel(component);
		
		Dimension size = component.getSize();
		int width=size.width;
        int height=size.height; 
		
        log.debug("Found width="+width+", height="+height);
        if(width==0 || height==0){
        	width=800;
        	height=600;
        	log.info("Component widht or height were zero: setting to default=800*600");
        	component.setSize(width, height);        	
        }        
        	
        //export
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
        Graphics2D grp = image.createGraphics();
        if(grp==null)
        	log.error("Could not save image in file "+f.getName()+": Graphics is null");
        else{        	
        	
        	if(SwingUtilities.getWindowAncestor(component)==null){        	
        		log.error("Could not export image of component because parent window is null: "+f.getName()+": perhaps close logging window?");   		        		
        	}
        	else{
        		component.paint(grp);
	        
		        //save image to file
		        try {
					ImageIO.write(image, "PNG", f);
				} catch (IOException e) {
					log.error("IOException while saving image: "+f.getAbsolutePath(), e);
				}        
        	}
        }
    }
	
}
