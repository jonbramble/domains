/*
 * #%L
 * Domain Plugins for ImageJ: a simple analysis tool for confocal image stacks of patchy bilayer membranes

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package ibios.domains;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.ImageCalculator;
import ij.plugin.PlugIn;
//import ij.plugin.Thresholder;
import ij.plugin.ZProjector;
//import ij.plugin.filter.LutApplier;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageConverter;
//import ij.process.ImageProcessor;
//import ij.process.ImageStatistics;
//import ij.process.AutoThresholder;



import java.io.File;
import java.io.IOException;

import loci.formats.FormatException;
//import loci.formats.ImageReader;
//import loci.formats.MetadataTools;
//import loci.formats.meta.IMetadata;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;

// Domains Processing
// ImageJ plugin by J Bramble nottingham.ac.uk
// 0.1 30/Oct/2014

/**
 * A simple analysis tool for confocal image stacks of patchy bilayer membranes
 *
 * @author Jonathan Bramble
 */

public class Domains implements PlugIn {

    //set up some class variables for the file names
    public String id;
    public String name;
    public String dir;      // set the default directory 
    private boolean abberation_correction = false;
    private boolean process_domains = false;
    private boolean verbose = false; 
    private double max_area = 5000.0;			//set maximum for domain size in particle analysis
    private double min_area = 0.0;			//set maximum for domain size in particle analysis
    private static double min_circ = 0.01;
    private static double max_circ = 1.0;
    
    private String[] colnames;
    private int num_cols;
    
    private String experiment = "experiment";
    private String batch;
    
    public void setMax_Area(double _max_area){
    	max_area = _max_area;
    }
    
    public void setMin_Area(double _min_area){
    	min_area = _min_area;
    }
    
    public void setAbberation_Correction(boolean _abberation_correction ){
    	abberation_correction = _abberation_correction;
    }
    
    public void setProcess_domains(boolean _process_domains ){
    	process_domains = _process_domains;
    }
    
    public void setExperiment(String _experiment){
    	experiment = _experiment;
    }
    
	public void setVerbose(boolean verbose) {
		// TODO Auto-generated method stub
		
	}
    
    public void run(String arg) {
    	
    	dir = OpenDialog.getDefaultDirectory();
    	
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog od = new OpenDialog("Open Leica file ...");  // no filter options in OpenDialog
        
        dir = od.getDirectory();
        name = od.getFileName();
        id = dir + name;
        String img_name;
        
        // create directory for processed files
        new File(dir+"processed").mkdirs();
        
        // remove the file ending for later
        name = name.replace(".lif", "");
        //name = name.replace(".lei", "");
            
        // processes the column names
        colnames = ColumnNames(experiment);
        num_cols = colnames.length;
        
        // import the images
        ImporterOptions options;		// new set of importer options from bio-formats
        
        try{
         options = new ImporterOptions();  
         options.setId(id);				// this is from dir + name from dialog box
         options.setAutoscale(true);
         options.setColorMode(ImporterOptions.COLOR_MODE_DEFAULT);
         options.setStackFormat(ImporterOptions.VIEW_STANDARD);
         options.setStackOrder(ImporterOptions.ORDER_XYCZT);
         options.setOpenAllSeries(true);   //this opens all the files, because I couldn't found out how to make a selector on the series name
         
            
         ImagePlus[] imps = BF.openImagePlus(options);
            for (ImagePlus imp : imps) {
                img_name = imp.getTitle();
                // only process the stacks
                if(imp.getImageStackSize()>1){        
                    IJ.showStatus("Processing Stacks "+img_name);
                    ProcessImageStack(imp);
                }
            }
            IJ.showStatus("Complete");
        }
        catch(IOException exc) {
            IJ.error("Sorry, an error occurred: " + exc.getMessage());
        }
        //catch(FormatException exc) {
         //   IJ.error("Sorry, a format error occurred: " + exc.getMessage());
        //}
        catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
          
    }
    
    private void ProcessImageStack(ImagePlus imp) {
        IJ.run(imp, "Magenta Hot", "");            // these are macro versions that I could rewrite in java 
                                				   // if I could find out how to load up the LUTs and apply them
        Calibration cal = imp.getCalibration();    //get calibrations from the stack image

        ZProjector zp = new ZProjector(imp);   // setup a zprojector and apply the average method
        zp.setMethod(ZProjector.AVG_METHOD);
        zp.doProjection();
        ImagePlus ave_img = zp.getProjection(); 
        ave_img.setCalibration(cal);	//this doesn't appear to have copied all the data over
                 
        ImagePlus ave_save = ave_img;// create a copy for saving
        ImageConverter ic = new ImageConverter(ave_save); // convert to 8bit grayscale for the averages
        ic.convertToGray8();
        ave_save.setCalibration(cal); 
        cal = ave_img.getCalibration();
              
        SaveImage(ave_save,"stack");

        // use a change of methods here to process the files - alternative schemes?
        if(process_domains==true){
        	ProcessProjections(ave_save);  // process the average images
        }
    }

    private void ProcessProjections(ImagePlus img){
    	
        // apply thresholding using macro style method
        IJ.run(img, "Auto Threshold", "method=Triangle");
                
        // Run median Filter
		img.getProcessor().medianFilter();
		
        // save the thresholded files       
        SaveImage(img,"threshold");

    	// remove abberations from images caused by high laser power
    	if(abberation_correction==true) {
        	ImagePlus corrected = RemoveAbberation(img);
        	img = corrected; 
    	}
    
        SaveImage(img,"mask");
        IJ.run(img, "Make Binary", ""); // added this to shut up the particleanalyser whinging about thresholded images

		// perform the particle analysis on the domains
        try{
        	DomainAnalysis(img);
        	}
        	catch(NullPointerException e ){
        	e.printStackTrace();
        }
 
    }

    private ImagePlus RemoveAbberation(ImagePlus imp){
		//need a way to save this in the plugin - but not vital as we might not need this on all samples at low laser power
        ImagePlus mask_img = IJ.openImage("/home/mbajb/mask_inv.png");        
        
        ImageCalculator ic = new ImageCalculator();
        ImagePlus imp_mask = ic.run("Add create", imp, mask_img);  
        imp_mask.setTitle(imp.getTitle());
        return imp_mask;
    }

    
    private void DomainAnalysis(ImagePlus imp){
    	
    	if(verbose==true){
    	  IJ.log("Min "+ String.valueOf(min_area));
    	  IJ.log("Max "+ String.valueOf(max_area));
    	}
    	
    	Calibration cal = imp.getCalibration();
    	double pixelHeight = cal.pixelHeight;
    	double pixelWidth = cal.pixelWidth;
    	double pixelArea = pixelHeight*pixelWidth;
    	double min_area_pixel = min_area/pixelArea;
    	double max_area_pixel = max_area/pixelArea;
    	
        ResultsTable rt = new ResultsTable();    
        RoiManager manager = new RoiManager(true); //create a hidden roi manager
        
        ParticleAnalyzer.setRoiManager(manager); // static method here
        //we could add a column here for the experiment name
        ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_OUTLINES, ParticleAnalyzer.FERET+ParticleAnalyzer.AREA+ParticleAnalyzer.AREA_FRACTION+ParticleAnalyzer.SHAPE_DESCRIPTORS, rt, min_area_pixel, max_area_pixel, min_circ, max_circ); 
 		pa.setHideOutputImage(true); 	
 		
 		boolean e = pa.analyze(imp);
 		if(e==true && verbose==true){
 			IJ.log("Analysed");
 		}

   		// retrieve the outline image from the particle analyser
        ImagePlus outlines_img= pa.getOutputImage(); 

		// save processed file
        SaveImage(outlines_img,"outlines");
        
        //this bit should match up the names
        String[] exp_name_variables = ExperimentName(outlines_img,num_cols);
        
        if(verbose==true){
        	IJ.log("Experiment " + experiment + " " + batch);
        	IJ.log("Number of Domains found " + String.valueOf(rt.getCounter()));
        }
        
		int numRows = rt.getCounter();
		for(int k=0;k<numRows;k++){
			for(int j=0; j<exp_name_variables.length;j++){
				rt.setValue(colnames[j],k,exp_name_variables[j]);
			}	
		}

		String newTitle = FileName(outlines_img,"dat");
		String analysis_file_name = dir+"processed/"+newTitle+".csv";
                   
        ImagePlus ave_img = OpenStackImage(imp,"stack");
        //region of interest based measurements on the original file
        int i;
        Roi[] rois;
        rois = manager.getRoisAsArray();
        for (i=0; i<rois.length; i++) {
           ave_img.setRoi(rois[i]); // here I need the ave img to apply the rois to
           double mean = ave_img.getStatistics().mean;
           double min = ave_img.getStatistics().min;
           double max = ave_img.getStatistics().max;
           rt.setValue("ROIMean",i, mean);
           rt.setValue("ROIMin",i, min); 
           rt.setValue("ROIMax",i, max); 
        }
        
        // this block saves the analysis file as a csv file in the directory        
        try{
            rt.saveAs(analysis_file_name);
        }
        catch(IOException exc){
            IJ.error("Sorry, an IO error occurred: " + exc.getMessage());
        }  
   
    }
    
    private ImagePlus OpenStackImage(ImagePlus imp, String type_suffix){
    	String newTitle = FileName(imp,type_suffix);		 
        String filename = dir + "processed/" + newTitle + ".tif"; // save the files
    	ImagePlus stack_image = IJ.openImage(filename);
    	return stack_image;  	
    }
    
    private void SaveImage(ImagePlus imp, String type_suffix){
    	 String newTitle = FileName(imp,type_suffix);	
         String filename = dir + "processed/" + newTitle + ".tif"; // save the files
    	 FileSaver fs = new FileSaver(imp);
    	 
  	     fs.saveAsTiff(filename);
    }
    
    // Process the given list of column headings
    private String[] ColumnNames(String experiment){
        // split the experiment name, assumes comma separated list of column headings
        experiment = experiment.replaceAll("\\s+","");
        String delims = "[,]";
        colnames = experiment.split(delims);
        
        if(verbose==true){
          IJ.log("Number of Column Names: " + String.valueOf(colnames.length));
          for( String col : colnames){
        	IJ.log(col);
          }
        }
        return colnames;
        	
    }
    
    private String FileName(ImagePlus imp, String type_suffix){
    	
    	String title = imp.getTitle().replaceAll("\\s+","");
        String delims = "[-]";
        String[] tokens = title.split(delims);
        String exp_var_title = tokens[1];							//remove the part before -
        
        String newTitle = exp_var_title.trim() + "-" + type_suffix;	
         
        if(verbose==true){
        	IJ.log("FileName Method");
        	for(String token : tokens){
        		IJ.log(token);
        	}
        }

        return newTitle;  	
    }
    
    
    // This should match up with the number of column headings
    private String[] ExperimentName(ImagePlus imp, int num_cols){
    	
    	String img_title = imp.getTitle().replaceAll("\\s+","");	//remove spaces
    	String delims = "[-]";
    	String[] tokens = img_title.split(delims);					//delimit based on -
    	String exp_var_title = tokens[1];							//remove the part before -
    	
    	// bioformats naming DROPS everything before the first _
    	// if 'name' has one or more underscore then replace exp_title with the second and subsequent tokens
    	// if it a full name then requires more testing
    	String replace_str = "";
    	String[] file_name_tokens = name.split("[_]");
    	for(int i=1;i<file_name_tokens.length;i++){
    		replace_str += file_name_tokens[i] + "_";
    		
    	}
    	
    	exp_var_title = exp_var_title.replace(replace_str, "");	
		
        delims = "[_]";												//delimit based on _
        tokens = exp_var_title.split(delims);	
       
        for(String token : tokens){
        	IJ.log(token);
        }
        
        if(tokens.length != num_cols){
        	IJ.log("Error: The number of column names does not match the number of variables");
        }
        
        for(String token : tokens){
        	token.trim();
        }
        
        return tokens; 
    }
    

    
   
}