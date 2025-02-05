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
pathToFile = 'C:/Users/Public/Documents/AAV Tropism Negative Controls/negative_control_results.csv'
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

exposureTimes = experimentalTissueExposures(pathToFolder)

println ("Experimental Tissue Exposures: "+exposureTimes)

rfpExposure = exposureTimes["RFP"]
gfpExposure = exposureTimes["GFP"]

tissueIntensities = negativeControlExposures(pathToFile)

println ("Negative Control Intensities: "+tissueIntensities)

tissueResults = findCells()

println ("Cells: "+tissueResults)

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

def experimentalTissueExposures(String pathToFolder)
{
    //Retrieve exposure time of experimental tissue/image to be analyzed
    def folderName = buildFilePath(pathToFolder)
    def secondaryIndex = -1
    
    def exposureTimes = ["RFP":0,"GFP":0,"DAPI":0]
    
    def rfpError = "No Error"
    def gfpError = "No Error"
    
    dh = new File(folderName)
    dh.eachFile {
        delimmiter = "\\"
        fileString = it as String
        //Remove the directory from the file name for ease of use
        fileString = fileString.substring(fileString.lastIndexOf(delimmiter)+1)
        //If the file is the animal we're looking for
        if(fileString.indexOf(animalNumber) > -1) {
            //Iterate over the file
            it.eachLine { line, number ->
                //If the tissue is misspelled in the exposure settings, still it should find it instead of throw an error
                if(tissueName == "testis") {secondaryIndex = line.indexOf("testes")}
                if(tissueName == "epididymis") {secondaryIndex = line.indexOf("epididymus")}
                //Line of tissue within the file that has the info we want
                if(line.toLowerCase().indexOf(tissueName) > -1 || secondaryIndex > -1)
                {
                    //Finds the point in the csv file string where we want, and returns them as separate strings
                    numberIndex = line.lastIndexOf(":")
                    exposures = line.substring(numberIndex+3).replaceAll("\"","")
                    if(channelCount == 3)
                    {
                    rfpString = exposures.substring(0, exposures.indexOf(","))
                    rfpNumbers = getIntsFromString(rfpString)

                    exposures = exposures.substring(exposures.indexOf(",")+1)
                    
                    gfpString = exposures.substring(0, exposures.indexOf(","))
                    gfpNumbers = getIntsFromString(gfpString)

                    dapiString = exposures.substring(exposures.indexOf(",")+1)
                    dapiNumbers = getIntsFromString(dapiString)
                    
                    //For each of the exposures, if they have two numbers, a fim was used, so it calculates the nearest actual exposure time
                    //Each exposure time is rounded to the nearest ten
                    println rfpNumbers
                    if (rfpNumbers.size() == 2) {
                        exposureTime = rfpNumbers[0]
                        FIM = rfpNumbers[1] / 100
                        
                        rfpExposure = Math.round((exposureTime * FIM)/10)*10
                        if (rfpExposure == 0) {rfpExposure = 10}
                    }
                    else {
                        rfpExposure = Math.round(rfpNumbers[0]/10)*10
                        if (rfpExposure as Integer == 0) {rfpExposure = 10}
                        if (rfpExposure > 200 && tissueName == "adrenal") { rfpError = "RFP exposure is too high to compare to controls: " + rfpExposure; rfpExposure = 100}
                        else if (rfpExposure > 200) { rfpError = "RFP exposure is too high to compare to controls: " + rfpExposure; rfpExposure = 200}
                    }
                    
                    if (gfpNumbers.size() == 2) {
                        exposureTime = gfpNumbers[0]
                        FIM = gfpNumbers[1] / 100
                        
                        gfpExposure = Math.round((exposureTime * FIM)/10)*10
                        if (gfpExposure == 0) {gfpExposure = 10}
                    }
                    else {
                        gfpExposure = Math.round(gfpNumbers[0]/10)*10
                        if (gfpExposure as Integer == 0) {gfpExposure = 10}
                        if (gfpExposure > 200 && tissueName == "adrenal") { gfpError = "GFP exposure is too high to compare to controls: " + gfpExposure; gfpExposure = 100}
                        else if (gfpExposure > 200) { gfpError = "GFP exposure is too high to compare to controls: " + gfpExposure; gfpExposure = 200}
                    }
                    
                    if (dapiNumbers.size() == 2) {
                        exposureTime = dapiNumbers[0]
                        FIM = dapiNumbers[1] / 100
                        
                        dapiExposure = Math.round((exposureTime * FIM)/10)*10
                        if (dapiExposure == 0) {dapiExposure = 10}
                    }
                    else {
                        dapiExposure = Math.round(dapiNumbers[0]/10)*10
                        if (dapiExposure as Integer == 0) {dapiExposure = 10}
                    }
                    
                    exposureTimes = ["RFP":rfpExposure as Double,"GFP":gfpExposure as Double,"DAPI":dapiExposure as Double,"RFP Error":rfpError,"GFP Error":gfpError]
                    }
                    else if(channelCount == 2)
                    {
                    rfpString = exposures.substring(0, exposures.indexOf(","))
                    rfpNumbers = getIntsFromString(rfpString)

//                    exposures = exposures.substring(exposures.indexOf(",")+1)
                    
                    dapiString = exposures.substring(exposures.indexOf(",")+1)
                    dapiNumbers = getIntsFromString(dapiString)
                    //For each of the exposures, if they have two numbers, a fim was used, so it calculates the nearest actual exposure time
                    //Each exposure time is rounded to the nearest ten
                    if (rfpNumbers.size() == 2) {
                        exposureTime = rfpNumbers[0]
                        FIM = rfpNumbers[1] / 100
                        
                        rfpExposure = Math.round((exposureTime * FIM)/10)*10
                        if (rfpExposure == 0 || rfpExposure == 10) {rfpExposure = 20}
                    }
                    else {
                        rfpExposure = Math.round(rfpNumbers[0]/10)*10
                        if (rfpExposure as Integer == 0) {rfpExposure = 10}
                        if (rfpExposure > 200 && tissueName == "adrenal") { rfpError = "RFP exposure is too high to compare to controls: " + rfpExposure; rfpExposure = 100}
                        else if (rfpExposure > 200) { rfpError = "RFP exposure is too high to compare to controls: " + rfpExposure; rfpExposure = 200}
                    }
                    
                    if (dapiNumbers.size() == 2) {
                        exposureTime = dapiNumbers[0]
                        FIM = dapiNumbers[1] / 100
                        
                        dapiExposure = Math.round((exposureTime * FIM)/10)*10
                        if (dapiExposure == 0) {dapiExposure = 10}
                    }
                    else {
                        dapiExposure = Math.round(dapiNumbers[0]/10)*10
                        if (dapiExposure as Integer == 0) {dapiExposure = 10}
                    }
                    
                    exposureTimes = ["RFP":rfpExposure as Double,"RFP Error":rfpError,"GFP Error":gfpError]
                    }             
                }
            }
        }
    }

    return exposureTimes
}

def negativeControlExposures(String pathToFile)
{
    //Get negative control RFP and GFP background for tissue at certain exposure
    //Setting preliminary values to determine if the tissue was found or not
    tissue_intensities = ["RFP":4096,"GFP":4096,"RFP_line":"","GFP_line":""]
    def foundExposure = false
    def closestExposure = [10000000, 0, 0, ""]
    
    //The file of negative control data
    filename = buildFilePath(pathToFile)
    File file = new File(filename)
    if(file.exists())
    {
        //Iterating over each line of the csv file
        new File(filename).eachLine { line, number ->
            if(number > 1) {
                //The first line (number) is the identifiers for each column, so it is skipped, then it checks for the tissue name in each line
                nameIndex = line.toLowerCase().indexOf(tissueName)
                
                //If the tissue is found
                if (nameIndex > -1)
                {
                    //Retrieve data in the line
                    indexOfComma = line.indexOf(',')
                    image_name = line.substring(0, indexOfComma).toLowerCase()
                    restOfString = line.substring(indexOfComma+1).toLowerCase()
                    
                    indexOfComma = restOfString.indexOf(',')
                    tissue_name = restOfString.substring(0, indexOfComma)
                    restOfString = restOfString.substring(indexOfComma+1)
                    
                    indexOfComma = restOfString.indexOf(',')
                    rfp_intensity = restOfString.substring(0, indexOfComma) as Double
                    restOfString = restOfString.substring(indexOfComma+1)
                    
//                    indexOfComma = restOfString.indexOf(',')
//                    rfp_min_mean = restOfString.substring(0, indexOfComma)
//                    restOfString = restOfString.substring(indexOfComma+1)
//                    
//                    indexOfComma = restOfString.indexOf(',')
//                    rfp_max_overall = restOfString.substring(0, indexOfComma)
//                    restOfString = restOfString.substring(indexOfComma+1)
//
//                    indexOfComma = restOfString.indexOf(',')
//                    rfp_min_overall = restOfString.substring(0, indexOfComma)
//                    restOfString = restOfString.substring(indexOfComma+1)
//                                        
//                    indexOfComma = restOfString.indexOf(',')
//                    rfp_stddev_max = restOfString.substring(0, indexOfComma)
//                    restOfString = restOfString.substring(indexOfComma+1)
//                                                            
//                    indexOfComma = restOfString.indexOf(',')
//                    rfp_stddev_min = restOfString.substring(0, indexOfComma)
//                    restOfString = restOfString.substring(indexOfComma+1)
                    
                    indexOfComma = restOfString.indexOf(',')
                    gfp_intensity = restOfString.substring(0, indexOfComma) as Double
                    restOfString = restOfString.substring(indexOfComma+1)
                    
                    indexOfComma = restOfString.indexOf(',')
                    nuclei_count = restOfString.substring(0, indexOfComma) as Double
                    restOfString = restOfString.substring(indexOfComma+1)
                
                    indexOfComma = restOfString.indexOf(',')
                    exposure_time = restOfString.substring(0, indexOfComma) as Double
                    restOfString = restOfString.substring(indexOfComma+1)

                    sex_of_animal = restOfString
                    
                    sexIndex = sex_of_animal.indexOf(sexOfAnimal)
                    
                    //Check if the sex of the animal is correct, then if the exposure time matches either gfp or rfp (different values)
                    if (sexIndex > -1)
                    {
                        if(rfpExposure == exposure_time)
                        {
                            tissue_intensities["RFP"] = Math.ceil(rfp_intensity) as Integer
                            tissue_intensities["RFP_line"] = line
                            foundExposure = true
                        }
                        if(gfpExposure == exposure_time)
                        {
                            tissue_intensities["GFP"] = Math.ceil(gfp_intensity) as Integer
                            tissue_intensities["GFP_line"] = line
                        }
                    }
                    if(!foundExposure)
                    {
                        if(rfpExposure >= exposure_time || rfpExposure <= exposure_time)
                        {
                            if(Math.abs(rfpExposure-exposure_time) < closestExposure[0])
                            {
                                closestExposure[0] = Math.abs(rfpExposure-exposure_time)
                                closestExposure[1] = Math.ceil(rfp_intensity) as Integer
                                closestExposure[2] = exposure_time
                                closestExposure[3] = line
                            }
                        }
                    }
                }
            }
        }
    }
    if(!foundExposure)
    {
        tissue_intensities["RFP"] = closestExposure[1]
        tissue_intensities["RFP_line"] = "Error with rfp exposure time mismatch, " + closestExposure[2] + "ms used instead of actual: " + rfpExposure + "ms; " + closestExposure[3]
    }
    //If the intensity is max of what it can be, the tissue was not found, so an error message is produced
    if(tissue_intensities["RFP"] == 4096)
    {
        println ("There was a problem with finding RFP intensity for "+imageName)
    }
    if(tissue_intensities["GFP"] == 4096)
    {
        println ("There was a problem with finding GFP intensity for "+imageName)
    }

    return tissue_intensities
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

    //The intensity from the negative controls is rounded up to the nearest hundred to have a higher confidence
    rfpIntensity = Math.ceil((tissueIntensities["RFP"]/100))*100
    gfpIntensity = Math.ceil((tissueIntensities["GFP"]/100))*100
    
    //Return to each variable the number of cells detected that are above a certain threshold, or all for the total number of cells
    rfpCellsIntensity = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > rfpIntensity}
    rfpCells500 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 500}
    rfpCells600 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 600}
    rfpCells700 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 700}
    rfpCells800 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 800}
    rfpCells900 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 900}
    rfpCells1000 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1000}
    rfpCells1100 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1100}
    rfpCells1200 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1200}
    rfpCells1300 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1300}
    rfpCells1400 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1400}
    rfpCells1500 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1500}
    rfpCells1600 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1600}
    rfpCells1700 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1700}
    rfpCells1800 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1800}
    rfpCells1900 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 1900}
    rfpCells2000 = getCellObjects().findAll{ measurement(it, "Cell: RFP mean") > 2000}
    
    gfpCells = getCellObjects().findAll{ measurement(it, "Cell: GFP mean") > gfpIntensity}
    detections = getDetectionObjects()
    
    //Finds the size of the array, rather than having cell objects
    totalRFPCellsIntensity = rfpCellsIntensity.size()
    totalrfpCells500 = rfpCells500.size()
    totalrfpCells600 = rfpCells600.size()
    totalrfpCells700 = rfpCells700.size()
    totalrfpCells800 = rfpCells800.size()
    totalrfpCells900 = rfpCells900.size()
    totalrfpCells1000 = rfpCells1000.size()
    totalrfpCells1100 = rfpCells1100.size()
    totalrfpCells1200 = rfpCells1200.size()
    totalrfpCells1300 = rfpCells1300.size()
    totalrfpCells1400 = rfpCells1400.size()
    totalrfpCells1500 = rfpCells1500.size()
    totalrfpCells1600 = rfpCells1600.size()
    totalrfpCells1700 = rfpCells1700.size()
    totalrfpCells1800 = rfpCells1800.size()
    totalrfpCells1900 = rfpCells1900.size()
    totalrfpCells2000 = rfpCells2000.size()
    
    totalGFPCells = gfpCells.size()

    //Classifies rfp cells as rfp + cells, and gfp cells as gfp +, and if they are positive for both, classifies them as positive for both
    rfpCellsIntensity.each{ it.setPathClass(getPathClass("Co-Localized RFP"))}
    getPathClass("Co-Localized RFP").setColor(getColorRGB(0,255,255))

    gfpCells.each{
        if(it.getPathClass() == getPathClass("Co-Localized RFP"))
        {
            it.setPathClass(getPathClass("Colocalized RFP and GFP"))
        } else {
            it.setPathClass(getPathClass("Co-Localized GFP"))
        }
    }
    getPathClass("Co-Localized GFP").setColor(getColorRGB(0,255,0))
    getPathClass("Co-Localized RFP and GFP").setColor(getColorRGB(255,0,255))
    fireHierarchyUpdate()
    
    cellResults["RFP"] = totalRFPCellsIntensity
    cellResults["totalrfpCells500"] = totalrfpCells500
    cellResults["totalrfpCells600"] = totalrfpCells600
    cellResults["totalrfpCells700"] = totalrfpCells700
    cellResults["totalrfpCells800"] = totalrfpCells800
    cellResults["totalrfpCells900"] = totalrfpCells900
    cellResults["totalrfpCells1000"] = totalrfpCells1000
    cellResults["totalrfpCells1100"] = totalrfpCells1100
    cellResults["totalrfpCells1200"] = totalrfpCells1200
    cellResults["totalrfpCells1300"] = totalrfpCells1300
    cellResults["totalrfpCells1400"] = totalrfpCells1400
    cellResults["totalrfpCells1500"] = totalrfpCells1500
    cellResults["totalrfpCells1600"] = totalrfpCells1600
    cellResults["totalrfpCells1700"] = totalrfpCells1700
    cellResults["totalrfpCells1800"] = totalrfpCells1800
    cellResults["totalrfpCells1900"] = totalrfpCells1900
    cellResults["totalrfpCells2000"] = totalrfpCells2000
    
    cellResults["GFP"] = totalGFPCells
    cellResults["DAPI"] = detections.size()
    
    return cellResults
}

def outputResultsFile() {
    //Returns the results of the program to a csv file in the project directory, detection results folder
    foldername = buildFilePath(PROJECT_BASE_DIR, 'detection results')
    filename = buildFilePath(foldername, 'results_July_2023.csv')
    filename_console = buildFilePath(foldername, 'console log.txt')
    mkdirs(foldername)

    //Appends the data to a file if it is already made, otherwise it appends the header row and then the data
    File resultsFile = new File(filename)
    File consoleFile = new File(filename_console)
    if(!resultsFile.exists())
    {
        resultsFile.append("Animal Number,Sex,Tissue Name,RFP Cell Count (@ Intensity),GFP Cell Count,DAPI Cell Count,Intensity 500,Intensity 600,Intensity 700,Intensity 800,Intensity 900,Intensity 1000,Intensity 1100,Intensity 1200,Intensity 1300,Intensity 1400,Intensity 1500,Intensity 1600,Intensity 1700,Intensity 1800,Intensity 1900,Intensity 2000\n")
    }
    tissueName = tissueName + animalInfo["tissue_identifier"]
    tissue_result = animalNumber+","+sexOfAnimal+","+tissueName+","+tissueResults["RFP"]+","+tissueResults["GFP"]+","+tissueResults["DAPI"]+","+tissueResults["totalrfpCells500"]+","+tissueResults["totalrfpCells600"]+","+tissueResults["totalrfpCells700"]+","+tissueResults["totalrfpCells800"]+","+tissueResults["totalrfpCells900"]+","+tissueResults["totalrfpCells1000"]+","+tissueResults["totalrfpCells1100"]+","+tissueResults["totalrfpCells1200"]+","+tissueResults["totalrfpCells1300"]+","+tissueResults["totalrfpCells1400"]+","+tissueResults["totalrfpCells1500"]+","+tissueResults["totalrfpCells1600"]+","+tissueResults["totalrfpCells1700"]+","+tissueResults["totalrfpCells1800"]+","+tissueResults["totalrfpCells1900"]+","+tissueResults["totalrfpCells2000"]
    if(tissueIntensities["RFP"] == 4096) {tissue_result = tissue_result+", error with RFP detection"}// || tissueIntensities["GFP"] == 4096
    tissue_result = tissue_result+"\n"
    resultsFile.append(tissue_result)
    
    consoleLog = "Animal Info: "+animalInfo+"\n"+"Experimental Tissue Exposures: "+exposureTimes+"\n"+"Negative Control Intensities: "+tissueIntensities+"\n"+"Cells: "+tissueResults+"\n\n"
    consoleFile.append(consoleLog)
    println("Done!")
}