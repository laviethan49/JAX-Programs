/* = CODE DESCRIPTION =
 * Implements linear unmixing as per the methods from
 * Neher & Neher, J. Microsc. 213, 1 (2004), p. 46-62
 * Zimmermann et al., FEBS Letters 531 (2992) p. 245-249
 * Inspired by the ImageJ plugin from Joachim Walter
 * https://imagej.nih.gov/ij/plugins/spectral-unmixing.html
 * 
 * 
 * == INPUTS ==
 * This script expect the following input folder configuration folder
 * Main Folder
 * 	- c1 [image name].tif
 * 	- c2 [image name].tif
 * 	- ...
 * 	- ROI Sets
 * 		- c1 [image name].tif.zip
 * 		- c2 [image name].tif.zip
 * 		- ...
 * 		
 * 	Each image is the individual control for channel 1, channel 2, etc...
 * 	
 * This configuration can be obtained using the BIOP MultiManual Select Tool
 * 
 * === ABOUT THE ROI Sets ===
 * There is no special name for the ROIs except one ROI per image that
 * MUST contain the letters 'BG' so this script knows which ROIs are background
 * 
 * Any other roi is considered as signal and the contribution of all ROIs will be averaged out
 * 
 * Finally, you must provide a list of the fluorophore names, comma separated 
 * 
 * == OUTPUTS ==
 * Running this code will produce an Umixing Parameters File to use with the
 * 'Linear Unmixing.groovy' script
 * 
 * = DEPENDENCIES =
 * An up to date installation of Fiji should be enough to get this script to run
 * 
 * = INSTALLATION = 
 * Simply open this script in the Fiji Script Interpreter and hit Run.
 * 
 * = AUTHOR INFORMATION =
 * Code written by Olivier Burri , EPFL - SV - PTECH - BIOP 
 * for Thierry Laroche, BIOP and Jessica Dessimoz, PTH
 * September 13th 2018
 * 
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2018
 * 
 * Licensed under GNU General Public License (GLP) version 3
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#@File (label="Directory with Controls and ROIs", style="directory") image_dir
#@File (label="Output Unmixing Parameters File", style="save") output_file
#@String (label="Fluorophore Names, comma separated") fluorophore_names

import ij.*
import ij.plugin.frame.RoiManager
import ij.gui.Roi
import Jama.Matrix

import groovy.json.*

def mm = new MixingMatrixBuilder(fluorophore_names)

// Work on current image


// Get images and associated ROI Sets
def roi_dir = new File(image_dir, "ROI Sets")
println(image_dir)

// Iterate to get all the control images
image_dir.eachFile{ file ->  
	if (file.name.endsWith("tif")){
	
	    def image_file = file.name[(0..-5)]
	    
	    def roi_file = new File(roi_dir, image_file+".zip")
	    
	    def image = IJ.openImage(file.getAbsolutePath())
	    
	    if( image != null && roi_file.exists() ) {
	    	println ("Parsing Data for "+file.name)
	    	// Open ROI Set
	    	def rm = new RoiManager(false)
			rm.runCommand("Open", roi_file.getAbsolutePath())
			def rois = rm.getRoisAsArray() as List
			
			// Images should start with c1, c2, etc so we can match which image is a control for which channel
			def channel = (file.name =~ /c(\d).*/).with { matches() ? it[0][1] as int : null }
	
			// This measures the ROIs and stores them into the mixing array
			mm.measureIntensities(image, channel, rois)
		
	    }
	}
    
}

// Save the arrays to JSON for further use
mm.saveToJson(output_file)

/**
 * This class allows us to build the mixing matrix, the fluorophore names and the background
 */
class MixingMatrixBuilder {
	int n_fluorophores
	Matrix mixing_matrix
	Matrix bg_matrix
	def fluo_names

	public MixingMatrixBuilder( String fluoro_names ) {
		
		// Parse the fluorophore names and initialize the matrices we'll be needing
		this.fluo_names = fluoro_names.tokenize(",").collect{it.trim()}
		this.n_fluorophores = fluo_names.size()
		this.mixing_matrix = new Matrix(n_fluorophores, n_fluorophores)
		this.bg_matrix = new Matrix(n_fluorophores, n_fluorophores)

	}

	// This measures the BG and intensities for a single channel on a control image
	public measureIntensities(image, channel, rois) {

		// Find the BG ROI
		Roi bg = rois.find{ it.getName().contains("BG") }

		// Get all the ROIs that are NOT the BG
		ArrayList<Roi> others = rois.findAll{ !it.getName().contains("BG") }

		// Now for all the channels of the image, get the intensities
		(1..n_fluorophores).each{fluorophore ->
			// Set the right image processor to measure
			def proc = image.getStack().getProcessor(fluorophore)
			proc.setRoi(bg)

			// Measure the background
			def bg_val = proc.getStatistics().mean
			bg_matrix.set(channel-1, fluorophore-1, bg_val)

			// Measure all the other ROIs
			def val=0
			others.each{roi ->
				proc.setRoi(roi)
				val += proc.getStatistics().mean
			}
			// Divide by number of ROIs to get average
			val /= others.size()
			

			// Final Mixing matrix value
			mixing_matrix.set(channel-1, fluorophore-1, val)
		}
		mixing_matrix.print(6,3)
	}

	/* 
	 * Saving a JSON object with 
	 * the fluorophore names
	 * the background values for each image
	 * the mixing matrix
	 */
	public saveToJson(jsonFile) {
		if (jsonFile.exists() ) jsonFile.delete()
		def json = jsonFile << JsonOutput.toJson([fluo_names, bg_matrix, mixing_matrix])
	}
}