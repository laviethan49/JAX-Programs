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
pathToFolder = 'C:/Users/Public/Documents/Negative Controls/'

//Housekeeping for setting up automated detections
if (channelCount == 2){ setChannelNames(imageData, "RFP", "DAPI") }
else { setChannelNames(imageData, "RFP", "GFP", "DAPI") }

//Array of any tissue names being used, some as misspellings to account for errors
tissueArray = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram", "testes"] as String []
sexArray = ["male", "female"]
fixationArray = ["pfa", "nbf"]

animalInfo = findAnimalInfo(imageName)

println ("Animal Info: "+animalInfo)

tissueResults = findCells()

findTissueExposures()

//To separate between iterations, makes it easier to look at
println " "

println tissueResults

outputResultsFile(tissueResults, animalInfo)

/************************************************Any and all functions are found below here, and called above. This slightly neatens the code up**************************************/
//Splits the title of the imag einto each of it's pieces split by a character
def splitTitle(String inputString)
{
    def outputString = []
    
    def numberIndex = inputString.indexOf("_")
    def restOfString = inputString
    def totalIndex = numberIndex

    def spaceIndex = inputString.indexOf(" ")
    
    while(numberIndex > -1 && totalIndex < spaceIndex) {
        currentString = restOfString.substring(0, numberIndex)
        outputString.add(currentString)
        restOfString = restOfString.substring(numberIndex+1)

        numberIndex = restOfString.indexOf("_")
        totalIndex = totalIndex + numberIndex
        if(totalIndex > spaceIndex)
        {
            finalString = restOfString.substring(0, restOfString.indexOf(" "))
            outputString.add(finalString)
        }
    }

    return outputString
}

//Based on the naming schema of our group, e.g. #231_NF_AAVrh74_Ai9Hom_MiddlePortion - adrenalgland_sl2_se2, to extract info
def findAnimalInfo(String current_image_name)
{    
    //These are variables for the name of the tissue, and if the tissue has more than one word, e.g. adrenal gland high exp x20, the identifier is everything past "adrenal"
    tissueIdentifier = ""
    tissueName = ""
    animalSex = ""
    fixation = ""

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
    {tissueName = current_image_name}
    else if(tissueName == "large" || tissueName == "small")
    {tissueIdentifier = " intestine"}
    else if(tissueName == "adrenal")
    {tissueIdentifier = " gland"}
    else if(tissueName == "lymph")
    {tissueIdentifier = " node"}
    else if(tissueName == "gastrocnemius")
    {tissueIdentifier = " muscle"}
    nameIndex = current_image_name.toLowerCase().indexOf("20x")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" 20x"}
    nameIndex = current_image_name.toLowerCase().indexOf("high")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" High Exp"}
    
    split_info = splitTitle(current_image_name)
    
    if (split_info.contains("f")) { animalSex = "F" }
    else { animalSex = "M" }
    
    if (split_info.contains("pfa")) { fixation = "PFA" }
    else if (split_info.contains("nbf")) { fixation = "NBF" }

    def animalMap = ["tissue_name":tissueName,"tissue_identifier":tissueIdentifier,"animal_sex":animalSex,"fixation_method":fixation]
    
    return animalMap
}

def findTissueExposures()
{
    return (imageName.substring(imageName.lastIndexOf("_")+1).replace(".tif", "").replace("ms", ""))
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

    detections = getDetectionObjectsAsArray()

    return findLowestSignal(detections)
}

def outputResultsFile(signalIntensities, animalInfo) {
    //Returns the results of the program to a csv file in the project directory, detection results folder
    def foldername = buildFilePath(PROJECT_BASE_DIR, 'Negative Exposures')
    def filename = buildFilePath(foldername, 'negative_control_results.csv')
    filename_console = buildFilePath(foldername, 'console log.txt')
    mkdirs(foldername)

    //Appends the data to a file if it is already made, otherwise it appends the header row and then the data
    File resultsFile = new File(filename)
    File consoleFile = new File(filename_console)
    if(!resultsFile.exists())
    {
        resultsFile.append("File Name,Tissue,RFP Max Mean,RFP Min Mean,RFP Max Overall,RFP Min Overall,RFP STD Dev Max,RFP STD Dev Min,GFP Max Mean,GFP Min Mean,GFP Max Overall,GFP Min Overall,GFP STD Dev Max,GFP STD Dev Min,DAPI Cell Count,Exposure Time (ms),Animal Sex\n")
    }
    
    tissueName = tissueName + animalInfo["tissue_identifier"]
    tissue_result = imageName+","+tissueName+","+signalIntensities['rfpMeanMax']+","+signalIntensities['rfpMeanMin']+","+signalIntensities['rfpMaxMax']+","+signalIntensities['rfpMinMin']+","+signalIntensities['rfpSTDDevMax']+","+signalIntensities['rfpSTDDevMin']+","+signalIntensities['gfpMeanMax']+","+signalIntensities['gfpMeanMin']+","+signalIntensities['gfpMaxMax']+","+signalIntensities['gfpMinMin']+","+signalIntensities['gfpSTDDevMax']+","+signalIntensities['gfpSTDDevMin']+","+detections.size()+','+findTissueExposures()+','+animalInfo['animal_sex']+"\n"
    
    if(signalIntensities['rfpMeanMin'] == 4096 || signalIntensities['gfpMeanMin'] == 4096) {tissue_result = tissue_result+", error with RFP or GFP detection"}
    
    tissue_result = tissue_result
    resultsFile.append(tissue_result)
}

def findLowestSignal(PathObject[] nuclei)
{    
    def rfpMeanMax = 0.0
    def rfpMeanMin = 4100.0
    def rfpMaxMax = 0.0
    def rfpMinMin = 4100.0
    def rfpSTDDevMax = 0.0
    def rfpSTDDevMin = 4100.0
    
    def gfpMeanMax = 0.0
    def gfpMeanMin = 4100.0
    def gfpMaxMax = 0.0
    def gfpMinMin = 4100.0
    def gfpSTDDevMax = 0.0
    def gfpSTDDevMin = 4100.0
    
    nuclei.each
    {
        rfpMean = measurement(it, "Cell: RFP mean")
        rfpMax = measurement(it, "Cell: RFP max")
        rfpMin = measurement(it, "Cell: RFP min")
        rfpSTDDev = measurement(it, "Cell: RFP std dev")
        
        gfpMean = measurement(it, "Cell: GFP mean")
        gfpMax = measurement(it, "Cell: GFP max")
        gfpMin = measurement(it, "Cell: GFP min")
        gfpSTDDev = measurement(it, "Cell: GFP std dev")
        
        if(rfpMean > rfpMeanMax)
        {
            rfpMeanMax = rfpMean
        }
        if(rfpMean < rfpMeanMin)
        {
            rfpMeanMin = rfpMean
        }
        if(rfpMax > rfpMaxMax)
        {
            rfpMaxMax = rfpMax
        }
        if(rfpMin < rfpMinMin)
        {
            rfpMinMin = rfpMin
        }
        if(rfpSTDDev > rfpSTDDevMax)
        {
            rfpSTDDevMax = rfpSTDDev
        }
        if(rfpSTDDev < rfpSTDDevMin)
        {
            rfpSTDDevMin = rfpSTDDev
        }
        
        if(gfpMean > gfpMeanMax)
        {
            gfpMeanMax = gfpMean
        }
        if(gfpMean < gfpMeanMin)
        {
            gfpMeanMin = gfpMean
        }
        if(gfpMax > gfpMaxMax)
        {
            gfpMaxMax = gfpMax
        }
        if(gfpMin < gfpMinMin)
        {
            gfpMinMin = gfpMin
        }
        if(gfpSTDDev > gfpSTDDevMax)
        {
            gfpSTDDevMax = gfpSTDDev
        }
        if(gfpSTDDev < gfpSTDDevMin)
        {
            gfpSTDDevMin = gfpSTDDev
        }
    }

    signalIntensities = [
        "rfpMeanMax": rfpMeanMax,
        "rfpMeanMin": rfpMeanMin,
        "rfpMaxMax": rfpMaxMax,
        "rfpMinMin": rfpMinMin,
        "rfpSTDDevMax": rfpSTDDevMax,
        "rfpSTDDevMin": rfpSTDDevMin,
        "gfpMeanMax": gfpMeanMax,
        "gfpMeanMin": gfpMeanMin,
        "gfpMaxMax": gfpMaxMax,
        "gfpMinMin": gfpMinMin,
        "gfpSTDDevMax": gfpSTDDevMax,
        "gfpSTDDevMin": gfpSTDDevMin
    ]

    return signalIntensities
}