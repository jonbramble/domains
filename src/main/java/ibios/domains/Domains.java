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
import ij.process.ImageConverter;
//import ij.process.ImageProcessor;
//import ij.process.ImageStatistics;
//import ij.process.AutoThresholder;




import java.io.File;
import java.io.IOException;

import loci.formats.FormatException;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
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
    private boolean abberation_correction = false;			// set to true if using di-8-anneps with 50% power
    private boolean process_domains = false;
    private double max_area = 1000.0;			//set maximum for domain size in particle analysis
    private double min_area =0.0;			//set maximum for domain size in particle analysis
    
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
    
    public void run(String arg) {
    	
    	dir = OpenDialog.getDefaultDirectory();
    	
        OpenDialog.setDefaultDirectory(dir);
        OpenDialog od = new OpenDialog("Open Leica .lei file ...");  // no filter options in OpenDialog
        
        dir = od.getDirectory();
        name = od.getFileName();
        id = dir + name;
        String img_name;

  		new File(dir+"processed").mkdirs();

        ImporterOptions options;		// new set of importer options from bio-formats
        /*
        //Ext.setId(id);
        //Ext.getSeriesCount(seriesCount);
        
        //IMetadata omexmlMetadata = MetadataTools.createOMEXMLMetadata();
        ImageReader reader = new ImageReader();
        //reader.setMetadataStore(omexmlMetadata);
        //reader.
        
        int seriesCount = reader.getSeriesCount();
        
        for (int i=0; i<seriesCount; i++) {
        	  reader.setSeries(i);
        	  //String name = omexmlMetadata.getImageName(i); // this is the image name stored in the file
        	  //String label = "Series " + (i + 1) + ": " + name;  // this is the label that you see in ImageJ
        	  // now you can read the pixel data for this series...
        	  //IJ.log(label);
        	}
        
        try {
			//reader.setId(id);
		} catch (FormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        
        
        IJ.log(String.valueOf(seriesCount));
        
        try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
        
        try{
         options = new ImporterOptions();  
         options.setId(id);
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

        String ave_file_name = dir+"processed/" +ave_save.getTitle()+".tif"; // save the files
        SaveImage(ave_save,ave_file_name);

        if(process_domains==true){
        	ProcessProjections(ave_save);  // process the average images
        }
    }

    private void ProcessProjections(ImagePlus imp){
        // apply thresholding using macro style method
        IJ.run(imp, "Auto Threshold", "method=Triangle");
        
        // Run median Filter
		imp.getProcessor().medianFilter();
		
        // save the thresholded files       
        String threshold_file_name = dir+"processed/" +imp.getTitle()+"-th.tif";
        SaveImage(imp,threshold_file_name);

    	// remove abberations from images caused by high laser power
    	if(abberation_correction==true) {
        	ImagePlus corrected = RemoveAbberation(imp);
        	imp = corrected; 
    	}
 
        String msk_file_name = dir+ "processed/" +imp.getTitle()+"-msk.tif";
        SaveImage(imp,msk_file_name);

		// perform the particle analysis on the domains
        DomainAnalysis(imp);
 
    }

    private ImagePlus RemoveAbberation(ImagePlus imp){
		//need a way to save this in the plugin - but not vital as we might not need this on all samples at low laser power
        ImagePlus mask_img = IJ.openImage("/home/mbajb/Data/Confocal/mask_inv.png");        
        
        ImageCalculator ic = new ImageCalculator();
        ImagePlus imp_mask = ic.run("Add create", imp, mask_img);  
        imp_mask.setTitle(imp.getTitle());
        return imp_mask;
    }

    private void DomainAnalysis(ImagePlus imp){
  
        ResultsTable rt = new ResultsTable();    
        ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.SHOW_OUTLINES, ParticleAnalyzer.FERET+ParticleAnalyzer.AREA+ParticleAnalyzer.AREA_FRACTION+ParticleAnalyzer.SHAPE_DESCRIPTORS, rt, min_area, max_area); 
 		pa.setHideOutputImage(true); 
 		boolean e = pa.analyze(imp);
 		if(e==true){
 			IJ.log("Analysed");
 		}

   		// retrieve the outline image from the particleanalyser
        ImagePlus outlines_img= pa.getOutputImage(); 
		//outlines_img.show();

		// save processed file
		String out_file_name = dir+ "processed/" +imp.getTitle()+"-out";
        SaveImage(outlines_img,out_file_name);

		IJ.log("Number of Domains found " + String.valueOf(rt.getCounter()));

		String analysis_file_name = dir+"processed/"+imp.getTitle()+"-dat.csv";
        
        // this block saves the analysis file as a csv file in the directory        
        try{
            rt.saveAs(analysis_file_name);
        }
        catch(IOException exc){
            IJ.error("Sorry, an io error occurred: " + exc.getMessage());
        }    
    }
    
    private void SaveImage(ImagePlus imp, String filename){
  	  FileSaver fs = new FileSaver(imp);
  	  fs.saveAsTiff(filename);
    }

}

// autothreshold methods not working here
//AutoThresholder auto_t = new AutoThresholder();
//ImageProcessor ip = imp.getProcessor();
//int [] hist = ip.getHistogram();
 //int t = auto_t.getThreshold("Triangle",hist);
//IJ.log(String.valueOf(t));
//ip.setAutoThreshold("Triangle",false);  // doesn't exist
//ip.threshold(t);

//LutApplier la = new LutApplier();
//la.setup("Magenta Hot",imp);
//la.run();
    
/*java.awt.image.IndexColorModel icm;
LutLoader lutlo = new LutLoader();
try {
        icm = lutlo.open("Magenta Hot");
}
catch(IOException exc){
}*/