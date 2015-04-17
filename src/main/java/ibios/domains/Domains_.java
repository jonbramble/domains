package ibios.domains;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.frame.PlugInFrame;

public class Domains_ extends PlugInFrame {
	
	public Domains_(){
		super("Domains");
	}

	public void run(String args){
		
		double domain_min = 0.0;
		double domain_max = 5000.0;
        GenericDialog gd = new GenericDialog("Processing Options");
        
        gd.addCheckbox("Process Domains",true);
        gd.addCheckbox("Abberation Correction",false);
        gd.addCheckbox("Verbose",false);
        gd.addMessage("Experiment Name");
        gd.addTextAreas("",null,1,30);
        
		gd.addNumericField("Minimum Domain Size:",domain_min,1);
		gd.addNumericField("Maximum Domain Size:",domain_max,1);
		
		// show the dialog and quit, if the user clicks "cancel"
		gd.showDialog();
		if (gd.wasCanceled()) {
			IJ.error("PlugIn canceled!");
			return;
		}
		
		boolean process_domains = gd.getNextBoolean();
		boolean abberation_correction = gd.getNextBoolean();
		boolean verbose = gd.getNextBoolean();
		String experiment = gd.getNextText();
		domain_min = (double)gd.getNextNumber();
		domain_max = (double)gd.getNextNumber();
			
		Domains md = new Domains();
		md.setAbberation_Correction(abberation_correction);
		md.setProcess_domains(process_domains);
		md.setVerbose(verbose);
		md.setExperiment(experiment);
		md.setMax_Area(domain_max);
		md.setMin_Area(domain_min);
		md.run(args);
	}
}
