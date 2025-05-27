/* = CODE DESCRIPTION =
 * Implements linear unmixing as per the methods from
 * Neher & Neher, J. Microsc. 213, 1 (2004), p. 46-62
 * Zimmermann et al., FEBS Letters 531 (2992) p. 245-249
 * Inspired by the ImageJ plugin from Joachim Walter
 * https://imagej.nih.gov/ij/plugins/spectral-unmixing.html
 * 
 * 
 * == INPUTS ==
 * This script assumes you have an Unmixing Parameters File that was produced using the 
 * 'Get_Linear_Unmixing_Parameters.groovy' script
 * 
 * You can either run the script on the currently open image OR on an image folder
 * 
 * == OUTPUTS ==
 * Running this code will either return the current image unmixed
 * OR
 * create an 'Unmixed' folder inside the user-provided input folder with all images 
 * umixed inside
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
 
#@File    (label="Directory", style="directory") image_dir
#@File    (label="Unmixing Parameters File") unmixing_params_file
#@Boolean (label="Process Only Current Image") is_current_only

import ij.*
import Jama.Matrix
import groovyx.gpars.GParsPool
import groovy.json.*
import groovy.io.FileType

def unmixer = new Unmixer(unmixing_params_file)

// Work on current image or on folder
if(is_current_only) {
	def image = IJ.	getImage()
	def unmixed = unmixer.unmix(image)
	unmixed.show()
} else {
	// Process the folder
	def save_dir = new File(image_dir, "Unmixed")
	save_dir.mkdir()
	// Get the files
	def files= []
	image_dir.eachFile (FileType.FILES) { file -> files << file }

	GParsPool.withPool(5) {
	files.eachParallel{ file ->  
	    def image = IJ.openImage(file.getAbsolutePath())
	    if( image != null ) {
			def unmixed = unmixer.unmix(image)
			IJ.	saveAsTiff(unmixed, new File(save_dir, unmixed.getTitle()).getAbsolutePath())
			unmixed.close()
			image.close()
		}  
	}
	}
}

print "DONE"

/** 
 * The Unmixer Class does all the dirty work. 
 * It computes the unmixing matrix from the Unmixing Parameters File
 * Then can Unmix any image passed to the unmix function
 */
class Unmixer {
	int n_channels
	Matrix mixing_matrix
	Matrix norm_mixing_matrix
	Matrix unmixing_matrix
	Matrix bg_matrix
	double[] total_int
	double[] avg_bg
	def fluo_names

	// Builder creates the matrices from the Unmixing Parameters File
	public Unmixer(parameters_file) {
		def jsonSlurper = new JsonSlurper()
		
		def matrices = jsonSlurper.parse(parameters_file)

		// First one is a list of the fluorophore names
		this.fluo_names = matrices[0]

		// We infer the number of fluorophores and channels from the row dimension of the matrices
		this.n_channels = matrices[1].rowDimension as int
		
		// Second is the background matrix
		this.bg_matrix     = new Matrix(matrices[1].columnPackedCopy as double[], n_channels)

		// Finally, we get the mixing matrix
		this.mixing_matrix = new Matrix(matrices[2].columnPackedCopy as double[], n_channels)

		// We initialize the average backgroung, which will be computed below
		avg_bg = new double[n_channels]

		// finish the calculation
		this.buildUnmixingMatrix()

	}

	/*
	 * Builds the unmixing matrix by
	 * 1. Getting the average backgroung for each channel
	 * 2. Subtracting that background to the mixing matrix, per channel
	 * 3. Normalizing the mixing matrix so each row sums to 1.0
	 * 4. Inverses the mixing matrix to get the unmixing matrix
	 */
	public buildUnmixingMatrix() {
		
		def all_channels = (0..bg_matrix.getColumnDimension()-1)
		def all_fluorophores = (0..bg_matrix.getRowDimension()-1)
		
		// Average BG
		all_channels.each{cha ->
			
			 avg_bg[cha] = 0
			
			all_fluorophores.each{ flu -> avg_bg[cha] += bg_matrix.get(cha, flu) }
			
			avg_bg[cha] /= bg_matrix.getRowDimension()
		}

		
		norm_mixing_matrix = mixing_matrix.copy()

		// Subtract BG, normalize
		all_fluorophores.each{flu ->
			 def total_int = 0
			all_channels.each{cha -> 
				norm_mixing_matrix.set(cha, flu, mixing_matrix.get(cha, flu) - avg_bg[cha] )
				total_int +=  norm_mixing_matrix.get(cha, flu)
			}
			all_channels.each{cha ->
				norm_mixing_matrix.set(cha, flu, norm_mixing_matrix.get(cha, flu) / total_int)
			}
		}
		
		unmixing_matrix = norm_mixing_matrix.inverse()
	}

	/*
	 * Performs unmixing on an ImagePlus by working on each pixel
	 * 1. Subtracting the average background to each channel
	 * 2. Multiplying the unmixing matrix by the vector created by taking the values of one pixel at all channels in
	 * 3. Places the ne pixels into a new image
	 */
	public unmix(ImagePlus original) {
	
		def width    = original.getWidth()
		def height   = original.getHeight()
		def channels = original.getNChannels()
		def slices   = original.getNSlices()
		def frames   = original.getNFrames()
		def luts     = original.getLuts()

		bg_matrix.print(5,3);
		mixing_matrix.print(5,3);
		norm_mixing_matrix.print(5,3);
		unmixing_matrix.print(5,3);
		
		
		def unmixed = 	IJ.createHyperStack(original.getTitle()+" - Unmixed", width, height, channels, slices, frames, 32)
		
		def start = new Date().getTime()
		// Loop through everything, parallelize to go faster
		GParsPool.withPool(10) {
		(0..width-1).eachParallel{ w ->
			(0..height-1).each {h ->
				(1..slices).each{ s ->
					(1..frames).each{ f ->
						// Get a vector of voxels where its size is 1 x n_channels
						def voxels = (1..channels).collect{ c ->
							def pos = original.getStackIndex(c, s, f)
							return original.getStack().getProcessor(pos).getf(w,h) - avg_bg[c-1]
						} as double[]
						// Create the vector, so as to use Jama matrix multiplication
						def vox_M = new Matrix(voxels, channels as int)

						def res = unmixing_matrix.times(vox_M)

						// Set the result values into the new image
						(1..channels).each{ c ->
							def pos = original.getStackIndex(c, s, f)
							unmixed.getStack().getProcessor(pos).setf(w,h,res.get(c-1,0) as float) 
						}
					}
				}
			}
		}
		}
		def end = new Date().getTime()
		println("unmix took "+(end-start)+"ms")

		// Cleanup, reset BC
		// Apply LUTs
		luts.eachWithIndex{ lut, c ->
			unmixed.setC(c+1)
			unmixed.setLut(lut)
			unmixed.resetDisplayRange()
			def max = unmixed.getDisplayRangeMax()
			unmixed.setDisplayRange(0, max)
			unmixed.setDisplayMode(IJ.COMPOSITE)
		}
		return unmixed
	}

	// Convenience methods to get the matrices if needed
	public getUnmixingMatrix() {
		return this.unmixing_matrix
	}

	public getNormMixingMatrix() {
		return this.norm_mixing_matrix
	}
	
	public getMixingMatrix() {
		return this.mixing_matrix
	}

	public getBGMatrix() {
		return this.bg_matrix
	}
}