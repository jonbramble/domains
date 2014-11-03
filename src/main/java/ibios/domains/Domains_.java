package ibios.domains;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.frame.PlugInFrame;

public class Domains_ extends PlugInFrame {
	
	public Domains_(){
		super("Domains");
	}

	public void run(String args){
        GenericDialog gd = new GenericDialog("FrameDemo settings");
		gd.addNumericField("Minimum Domain:",0.0,1);
		gd.addNumericField("Maximum Domain:",1000.0,1);
		
		// show the dialog and quit, if the user clicks "cancel"
		gd.showDialog();
		if (gd.wasCanceled()) {
			IJ.error("PlugIn canceled!");
			return;
		}
		
		//pass on the arguments to domains constructor
		
		
		Domains md = new Domains();
		md.run(args);
	}
}
