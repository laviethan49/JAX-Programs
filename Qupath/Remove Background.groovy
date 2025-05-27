import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
/*
* SCGE - SATC - Jackson Laboratory - Murray Lab - Ethan Saville
* QuPath 0.3.2
* Program Version: a-0.9
* This program was made to allow for rapid throughput image analasys for the AAV tropism study conducted.
* It works by using an excel spreadsheet of negative control animal info, comparing it to an experimental animal's tissue that it matches,
* finds RFP and GFP positive signal based on the "background" intensity found in the negative controls.
* The signal is found from within detected cells based on a Hoechst nuclear stain, imaged as DAPI on a fluorescence microscope.
* The result of the program is a comma separated value file of the animal number, sex, tissue name, RFP cell count, GFP cell count, and nuclei count
*/

/*
* Bug Fixes/ Features Needed
* More testing
* If an experimental tissue has an exposure time that is not found in the negative control excel sheet, use the nearest one if applicable (check other sex), otherwise, note it as an error
* Add more error handling to find the issue, while most of this has been taken care of, but new issues can arise
* Change how variables outside of functions are named (camelCase) and inside of functions (no_camel_case)
*/

//Gets data of current image, server of current image data, count of the channels in the image, and image name
imageData = getCurrentImageData()
server = imageData.getServer()
channelCount = server.nChannels()
imageName = getProjectEntry().getImageName().toLowerCase()
//The folder is the location of all of the experimental animals exposure settings, the file is the location of the negative control intensities at given exposure times
pathToFolder = 'C:/Users/Public/Documents/AAV Tropism Exposure Settings'
pathToFile = 'C:/Users/Public/Documents/QuPath/AAV Tropism Negative Controls/detection results/negative_control_results.csv'
//pathToFile = 'C:/Users/Public/Documents/QuPath/AAV9-sg Negative Controls/Negative Exposures/negative_control_results.csv'

//Housekeeping for setting up automated detections
if (channelCount == 2){ setChannelNames(imageData, "RFP", "DAPI") }
else { setChannelNames(imageData, "RFP", "GFP", "DAPI") }

//Array of any tissue names being used, some as misspellings to account for errors
tissueArray = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram", "testes"] as String []

animalInfo = findAnimalInfo(imageName)

println ("Animal Info: "+animalInfo)

sexOfAnimal = animalInfo["animal_sex"]
tissueName = animalInfo["tissue_name"]

tissueResults = findCells()

//println ("Cells: "+tissueResults)

//To separate between iterations, makes it easier to look at
println " "

outputResultsFile()

/************************************************Any and all functions are found below here, and called above. This slightly neatens the code up**************************************/

//Finds the animal number from the imageName. Finds numbers in the string, and uses the highest one as the animal number.
def getAnimalNumber(String input) {
  def animalNumber = 0
  def numbers = input.findAll( /\d+/ )*.toInteger()
  numbers.each {
      if(it > animalNumber) {animalNumber = it}
  }
  return animalNumber  
}

//Same as above, but instead of returning the highest integer, it returns all of them as an array
def getIntsFromString(String input) {
  def numbers = input.findAll( /\d+/ )*.toInteger()

  return numbers
}

//Based on the naming schema of our group, e.g. #231_NF_AAVrh74_Ai9Hom_MiddlePortion - adrenalgland_sl2_se2, to extract info
def findAnimalInfo(String current_image_name)
{
    //numberIndex is a recurring variable for the index in a string for what is being looked for
    numberIndex = current_image_name.indexOf(" ")
    animalNumber = getAnimalNumber(current_image_name) as String

    numberIndex = current_image_name.indexOf(animalNumber)
    
    sexOfAnimal = current_image_name.substring(numberIndex)
    sexOfAnimal = sexOfAnimal.substring(sexOfAnimal.indexOf("_"), sexOfAnimal.indexOf("_")+5)
    
    if (sexOfAnimal.indexOf("m") > -1) {sexOfAnimal = "m"}
    else if (sexOfAnimal.indexOf("f") > -1) {sexOfAnimal = "f"}
    
    //These are variables for the name of the tissue, and if the tissue has more than one word, e.g. adrenal gland high exp x20, the identifier is everything past "adrenal"
    tissueIdentifier = ""
    tissueName = ""

    //Iterates through the array listed above to have a consistent name
    tissueArray.each
    {
        nameIndex = current_image_name.indexOf(it)
        if(nameIndex != -1)
        {
            tissueName = it
        }
    }
    
    //Error correction for searching through excel files
    if(tissueName == "testes")
    {tissueName = "testis"}
    else if(tissueName == "diaphram")
    {tissueName = "diaphragm"}
    else if(tissueName == "muscle")
    {tissueName = "gastrocnemius"}
    else if(tissueName == "lg")
    {tissueName = "large"}
    else if(tissueName == "sm")
    {tissueName = "small"}
    else if(tissueName == "epididymus")
    {tissueName = "epididymis"}
    
    //Adding the tissue identifiers
    if(tissueName == "")
    {tissueName = imageName}
    else if(tissueName == "large" || tissueName == "small")
    {tissueIdentifier = " intestine"}
    else if(tissueName == "adrenal")
    {tissueIdentifier = " gland"}
    else if(tissueName == "lymph")
    {tissueIdentifier = " node"}
    else if(tissueName == "gastrocnemius")
    {tissueIdentifier = " muscle"}
    nameIndex = imageName.toLowerCase().indexOf("20x")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" 20x"}
    nameIndex = imageName.toLowerCase().indexOf("high")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" High Exp"}
    
    def animalMap = ["animal_number":animalNumber,"animal_sex":sexOfAnimal,"tissue_name":tissueName,"tissue_identifier":tissueIdentifier]
    
    return animalMap
}


//Function to find the cells in the "DAPI" channel, whih in our case is the Hoechst stained nuclei
def findCells() {
    //Remove all detections and annotations
    clearAllObjects()
    //Empty map for variable placeholder
    def cellResults = [:]
    //Size of the image
    def xdist = server.getWidth()
    def ydist = server.getHeight()

    //If there are multiple z planes (nmore than 0), there is only 1 in our case
    if (server.nZSlices() > 0){
        //For each z plane, make a rectangle annotation the size of the image, and add it to the image
        0.upto(server.nZSlices()-1){
            frame = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0,0,xdist,ydist,ImagePlane.getPlane(it,0)));
            addObject(frame);
        }
    }

    //Select the rectange we just made (annotations in this case is only the one)
    selectAnnotations()

    //Based on the tissue name, run the cell detection protocol with slightly varied inputs
    switch(tissueName) {
        case "adrenal":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "bladder":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "brain":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "diaphragm":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "eye":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 1,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "gastrocnemius":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "heart":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "kidney":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "large":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "liver":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "lung":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "lymph":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "pancreas":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "skin":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "small":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.6,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "spleen":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "stomach":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.5,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "thymus":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "epididymis":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "ovary":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "testis":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.9,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        case "uterus":
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.7,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
        default:
            runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage": "DAPI",  "requestedPixelSizeMicrons": 0.5,  "backgroundRadiusMicrons": 18,  "medianRadiusMicrons": 0,  "sigmaMicrons": 0.8,  "minAreaMicrons": 10.0,  "maxAreaMicrons": 400.0,  "threshold": 100.0,  "watershedPostProcess": true,  "cellExpansionMicrons": 5.0,  "includeNuclei": false,  "smoothBoundaries": true,  "makeMeasurements": true}');
            break;
    }

    //Return to each variable the number of cells detected that are above a certain threshold, or all for the total number of cells
//    
//    
//    gfpCells = getCellObjects().findAll{ measurement(it, "Cell: GFP mean") > gfpIntensity}
    detections = getDetectionObjectsAsArray()
    cellResults["Cells"] = detections

    rfp_list = detections.collect{detections -> measurement(detections, "Cell: RFP mean")} as double[]
    
    StatsHelper = new DescriptiveStatistics(rfp_list)
    percentage = 95
    perc_cells = StatsHelper.getPercentile(percentage)
    sd_cells = StatsHelper.getStandardDeviation()
    mean_cells = StatsHelper.getMean()
//    mode_cells = mode(rfp_list)
    sortedVals = StatsHelper.getSortedValues()
    
    roundTens(sortedVals)
    println sortedVals
    mode_val = mode(sortedVals)
    println mode_val
    
    rfpCellsIntensity = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > perc_cells}
    cellResults["rfpCells"] = rfpCellsIntensity.size()
    cellResults["rfpIntensity"] = perc_cells
    cellResults["rfpSTDDEV"] = sd_cells
    cellResults["rfpMean"] = mean_cells
    
//    
//    //Finds the size of the array, rather than having cell objects
//    totalRFPCellsIntensity = rfpCellsIntensity.size()
//    
//    totalGFPCells = gfpCells.size()
//
//    //Classifies rfp cells as rfp + cells, and gfp cells as gfp +, and if they are positive for both, classifies them as positive for both
//    rfpCellsIntensity.each{ it.setPathClass(getPathClass("Co-Localized RFP"))}
//    getPathClass("Co-Localized RFP").setColor(getColorRGB(0,255,255))
//
//    gfpCells.each{
//        if(it.getPathClass() == getPathClass("Co-Localized RFP"))
//        {
//            it.setPathClass(getPathClass("Colocalized RFP and GFP"))
//        } else {
//            it.setPathClass(getPathClass("Co-Localized GFP"))
//        }
//    }
//    getPathClass("Co-Localized GFP").setColor(getColorRGB(0,255,0))
//    getPathClass("Co-Localized RFP and GFP").setColor(getColorRGB(255,0,255))
//    fireHierarchyUpdate()
//    
//    cellResults["RFPIntense"] = totalRFPCellsIntensity
//    
//    cellResults["GFP"] = totalGFPCells
    cellResults["DAPI"] = detections.size()
    
    return cellResults
}

def roundTens(double[] a)
{
    a.eachWithIndex{rfpNum, index ->
        rounded = Math.round(rfpNum/10)*10
        a[index] = rounded
    }
}

def mode(double[] a) {
    int maxValue, maxCount;

    for (int i = 0; i < a.length; ++i) {
        int count = 0;
        for (int j = 0; j < a.length; ++j) {
            if (a[j] == a[i]) ++count;
        }
//        currentVal = [a[i], count]
//        println currentVal
        if (count > maxCount) {
            maxCount = count;
            maxValue = a[i];
        }
    }

    return [maxValue, maxCount];
}
def outputResultsFile() {
    //Returns the results of the program to a csv file in the project directory, detection results folder
    folder_name = buildFilePath(PROJECT_BASE_DIR, 'detection results')
    file_name = buildFilePath(folder_name, 'results.csv')
    console_file_name = buildFilePath(folder_name, 'console log.txt')
    mkdirs(folder_name)

    //Appends the data to a file if it is already made, otherwise it appends the header row and then the data
//    File resultsFile = new File(filename)
    File consoleFile = new File(console_file_name)
//    if(!resultsFile.exists())
//    {
//        resultsFile.append("Animal Number,Sex,Tissue Name,RFP Cell Count (@ Intensity),GFP Cell Count,DAPI Cell Count,Intensity 500,Intensity 600,Intensity 700,Intensity 800,Intensity 900,Intensity 1000,Intensity 1100,Intensity 1200,Intensity 1300,Intensity 1400,Intensity 1500,Intensity 1600,Intensity 1700,Intensity 1800,Intensity 1900,Intensity 2000\n")
//    }
//    tissueName = tissueName + animalInfo["tissue_identifier"]
//    tissue_result = animalNumber+","+sexOfAnimal+","+tissueName+","+tissueResults["RFPIntense"]+","+tissueResults["GFP"]+","+tissueResults["DAPI"]+","+tissueResults["totalrfpCells500"]+","+tissueResults["totalrfpCells600"]+","+tissueResults["totalrfpCells700"]+","+tissueResults["totalrfpCells800"]+","+tissueResults["totalrfpCells900"]+","+tissueResults["totalrfpCells1000"]+","+tissueResults["totalrfpCells1100"]+","+tissueResults["totalrfpCells1200"]+","+tissueResults["totalrfpCells1300"]+","+tissueResults["totalrfpCells1400"]+","+tissueResults["totalrfpCells1500"]+","+tissueResults["totalrfpCells1600"]+","+tissueResults["totalrfpCells1700"]+","+tissueResults["totalrfpCells1800"]+","+tissueResults["totalrfpCells1900"]+","+tissueResults["totalrfpCells2000"]
//    if(tissueIntensities["RFP"] == 4096) {tissue_result = tissue_result+", error with RFP detection"}// || tissueIntensities["GFP"] == 4096
//    tissue_result = tissue_result+"\n"
//    resultsFile.append(tissue_result)
    positivePerc = (tissueResults["rfpCells"]/tissueResults["DAPI"])*100

    consoleLog = "Animal Info: "+animalInfo+"\nRFP + Threshold: "+tissueResults["rfpIntensity"]+"\nRFP Std Dev: "+ tissueResults["rfpSTDDEV"]+"\nRFP Mean: "+tissueResults["rfpMean"]+"\nRFP + Cell Count: "+tissueResults["rfpCells"]+"\nTotal Detected Cells: "+tissueResults["DAPI"]+"\nPositive RFP Cell Percentage: "+positivePerc+"%\n\n"
    consoleFile.append(consoleLog)
}