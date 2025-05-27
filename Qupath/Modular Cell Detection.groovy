//Check if file for descriptions has been made for project yet, then if not, make it. If it is made, read it.
image_data = getCurrentImageData()
server = image_data.getServer()
channel_count = server.nChannels()
image_name = getProjectEntry().getImageName().toLowerCase()
tissue_array = ["epididymus", "lg", "sm", "muscle", "adrenal", "bladder", "brain", "diaphragm", "eye", "gastrocnemius", "heart", "kidney", "large", "liver", "lung", "lymph", "pancreas", "skin", "small", "spleen", "stomach", "thymus", "epididymis", "ovary", "testis", "uterus", "diaphram", "testes"] as String []
default_folder = buildFilePath('C:/Users/Public/Documents/')
default_file = buildFilePath(default_folder, 'negative_control_exposures.csv')
default_folder = new File(default_folder)
default_file = new File(default_file)

if (channel_count == 2){ setChannelNames(image_data, "RFP", "DAPI") }
else { setChannelNames(image_data, "RFP", "GFP", "DAPI") }

//Prompt user for directory of exposure times for experimental animals
//Prompt user for file of negative control exposures
//Both only if needed, or the animals are experimental

if(!check_file())
{
    println("File does not exist...")
    file_name_input = Dialogs.showInputDialog("File Name Format", "Please type an example of the format for the names of the files sepearated by underscores", "Genotype_Sex_Allele_Fixation")
    negative_positive_choice = Dialogs.showYesNoDialog("Negative Control or Experimental", "Select yes if this is for experimental animals, or select no if this is for negative control animals")
    
    identifiers = split_file_name(file_name_input)
    experimental_animals = negative_positive_choice

    make_file(identifiers, experimental_animals)   
}

def fileNames = split_file_name(image_name)
def currentIdentifiersAll = read_file(fileNames)
def currentIdentifiers = currentIdentifiersAll[0]
def projectIdentifiers = currentIdentifiersAll[1]
def indices = currentIdentifiersAll[2]
def exposureTimes = []

if(currentIdentifiers["Experimental"].toLowerCase() == "true")
{
    //Gets the negative intensity settings from a file with all of the relevant info
    intensitySettings = get_intensity_settings(projectIdentifiers, currentIdentifiers, indices)
    //Perform cell discovery, and count/mark the cells above the negative threshold for given channels
    
//    intensitySettings.each{it.each{value -> println value}}
}
else
{
    //Append exposure settings file lines with intensity observed
    get_negative_intensities(currentIdentifiers, indices)
}

def get_negative_intensities(entryIdentifiers, indexIdentifiers)
{
//    println "Appending negative control intensities to project file..."
    def folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
    def fileName = buildFilePath(folderName, 'negative_control_intensities.csv')
    mkdirs(folderName)
    File resultsFile = new File(fileName)
    if(resultsFile.exists())
    {
        println "File exists already..."
        return
    }
    
    def nucleiTotal = find_cells(entryIdentifiers)
    def signalIntensities = find_signals(nucleiTotal)
    def signalIdentifiers = signalIntensities[1]
    signalIntensities = signalIntensities[0]
    
    header = "File Name,Tissue,Nuclei Count,Sex,Exposure Time"
    signalIdentifiers.each {
        header = header+","+it
    }
    resultsFile.append(header)
    
    msIndex = image_name.lastIndexOf("ms")
    spaceIndex = image_name.lastIndexOf("_")+1
    
    if(spaceIndex != -1 && msIndex != -1)
    {
        exposureTime = image_name.substring(spaceIndex, msIndex) as Integer
    }
    else
    {
        exposureTime = "N/A"
    }
    
    rowValue = "\n"+image_name+","+entryIdentifiers["Tissue"]+","+nucleiTotal.size()+","+entryIdentifiers["Sex"]+","+exposureTime
    signalIdentifiers.each {
        rowValue = rowValue+","+signalIntensities[it]
    }

    resultsFile.append(rowValue)
}

def split_string_by_identifier(String inputString, String identifier)
{
    def outputStrings = []
    again = false
    values = inputString
    commaIndex = values.indexOf(identifier)
    i = 0
    while(commaIndex != -1 || again)
    {
        if(again)
        {
            currentValue = values
            again = false
        }
        else
        {
            currentValue = values.substring(0,commaIndex)
            values = values.substring(commaIndex+1)
            commaIndex = values.indexOf(",")
            if(commaIndex == -1) again = true
        }
        outputStrings[i] = currentValue
        i += 1
    }
    return outputStrings
}

def get_intensity_settings(globalIdentifiers, entryIdentifiers, indexIdentifiers)
{
    def exposuresStored = false
    def folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
    def fileName = buildFilePath(folderName, 'file_name_descriptors.csv')
    def identifierResults = []
    def bestMatch = 0
    def experimentalFile = ""
    def negativeFile = ""
    def exposureIdentifiers = [:]
    def headers = ""
    def values = ""
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    resultsFile.eachLine {line, index ->
        def nextIndex = line.indexOf(",")
        def nextString = line
            
        while(nextIndex != -1)
        {
            currentString = nextString.substring(0, nextIndex)
            identifierResults += [currentString]
            nextString = nextString.substring(nextIndex+1)
            nextIndex = nextString.indexOf(",")
        
            if(nextIndex == -1)
            {
                identifierResults += [nextString]
            }
        }
    }

    //If line contains identifiers for experimental and negative exposure times, they were already found
    if(identifierResults.contains("path_to_experimental"))
    {
        exposuresStored = true
        experimentalIndex = identifierResults.indexOf("path_to_experimental")
        exposureSettingsExperimental = new File(identifierResults[experimentalIndex+1])
        negativeIndex = identifierResults.indexOf("path_to_negative")
        exposureSettingsNegative = new File(identifierResults[negativeIndex+1])
    }
    
    while(globalIdentifiers.contains("true") && !exposuresStored)
    {
        if(!binding.hasVariable('exposureSettingsExperimental') || exposureSettingsExperimental == null)
        {
            Dialogs.showInfoNotification("Choose Folder Where Exposure Settings For Experimental Animals Are", "Choose The Folder Where The Exposure Settings For The Experimental Animals Are")
            exposureSettingsExperimental = Dialogs.promptForDirectory(default_file)
        }
        if(!binding.hasVariable('exposureSettingsNegative') || exposureSettingsNegative == null)
        {
            Dialogs.showInfoNotification("Choose File With Negative Control Exposure Settings", "Choose The File With The Negative Control Exposure Settings")
            exposureSettingsNegative = Dialogs.promptForFile(default_file)
        }
        if(exposureSettingsExperimental != null && exposureSettingsNegative != null)
        {
            exposuresStored = true
            resultsFile.append(",path_to_experimental,"+exposureSettingsExperimental+",path_to_negative,"+exposureSettingsNegative)
        }
    }
    //Get exposure times for experimental and negative associated with experimental, not just return file path

    //For each file, see if the identifiers match, and use the file that has the most matches
    exposureSettingsExperimental.eachFile{
        matches = 0
        delimmiter = "\\"
        fileString = it as String
        //Remove the directory from the file name for ease of use
        fileString = fileString.substring(fileString.lastIndexOf(delimmiter)+1)
        indexIdentifiers.each{identifier ->
            if(fileString.toLowerCase().contains("_"+entryIdentifiers[identifier])) matches++
        }
        if(bestMatch < matches)
        {
            experimentalFile = fileString
            bestMatch = matches
        }
    }
    experimentalFile = buildFilePath(exposureSettingsExperimental as String, experimentalFile)
    
    experimentalPlaceholder = []
    experimentalSettings = [:]
    new File(experimentalFile).eachLine{line, index ->
        if(line.contains("exposure"))
        {
            again = false
            headers = line
            commaIndex = headers.indexOf(",")
            while(commaIndex != -1 || again)
            {
                if(again)
                {
                    currentHeader = headers
                    again = false
                }
                else
                {
                    currentHeader = headers.substring(0,commaIndex)
                    headers = headers.substring(commaIndex+1)
                    commaIndex = headers.indexOf(",")
                    if(commaIndex == -1) again = true
                }
                experimentalPlaceholder = experimentalPlaceholder + currentHeader
            }
        }
        if(line.contains(entryIdentifiers["Tissue"]))
        {
            values = line.replace("\"", "")
            replaceVal = values.substring(values.indexOf("block")-2, values.indexOf(":")+1)
            values = values.replace(replaceVal, "")
            again = false
            
            commaIndex = values.indexOf(",")
            i = 0
            while(commaIndex != -1 || again)
            {
                if(again)
                {
                    currentValue = values
                    again = false
                }
                else
                {
                    currentValue = values.substring(0,commaIndex)
                    values = values.substring(commaIndex+1)
                    commaIndex = values.indexOf(",")
                    if(commaIndex == -1) again = true
                }
                experimentalSettings[experimentalPlaceholder[i]] = currentValue
                i += 1
            }
        }
    }

    experimentalExposures = [:]
    experimentalPlaceholder.each
    {
        if(it.toLowerCase().contains("y3") || it.toLowerCase().contains("rfp") && it.toLowerCase().contains("exposure")) experimentalExposures["RFP"] = experimentalSettings[it]
        if(it.toLowerCase().contains("gfp") && it.toLowerCase().contains("exposure")) experimentalExposures["GFP"] = experimentalSettings[it]
        if(it.toLowerCase().contains("dapi") || it.toLowerCase().contains("hoechst") && it.toLowerCase().contains("exposure")) experimentalExposures["DAPI"] = experimentalSettings[it]
    }
    
    formatOfNegativeFile = []
    currentExposureSettings = [:]
    tissueNegativeSettings = [:]

    exposureSettingsNegative.eachLine {line, index ->
        if(index == 1) {formatOfNegativeFile = split_string_by_identifier(line, ",")}
        else
        {
            split_string_by_identifier(line, ",").eachWithIndex {value, key ->
                currentExposureSettings[formatOfNegativeFile[key]] = value
            }
        }
        
        if((currentExposureSettings["Tissue"] == entryIdentifiers["Tissue"]) && (currentExposureSettings["Exposure Time"] == experimentalExposures["GFP"]))
        {
            formatOfNegativeFile.eachWithIndex {value, key ->
                tissueNegativeSettings[value] = currentExposureSettings[value]
            }
        }
    }
    
    return [formatOfNegativeFile, tissueNegativeSettings]
}

def split_file_name(String inputString)
{
    if(inputString == null) {return ["User Cancelled"]}
    
    def identifierResults = []
    def nextIndex = inputString.indexOf("_")
    def spaceIndex = inputString.indexOf(" ")
    def nextString = inputString
    def lastString = ""

    if(nextIndex == -1) {identifierResults = ["No Seperators Found", inputString]}
    if(spaceIndex > -1) {nextString = nextString.substring(0, spaceIndex); lastString = inputString.substring(spaceIndex+3)}
    
    while(nextIndex != -1)
    {
        currentString = nextString.substring(0, nextIndex)
        identifierResults += [currentString]
        nextString = nextString.substring(nextIndex+1)
        nextIndex = nextString.indexOf("_")
        
        if(nextIndex == -1)
        {
            identifierResults += [nextString]
        }
    }
    identifierResults += [lastString]
    
    return identifierResults
}

def check_file()
{
    def folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
    def fileName = buildFilePath(folderName, 'file_name_descriptors.csv')
    mkdirs(folderName)

    File resultsFile = new File(fileName)

    return resultsFile.exists()
}

def make_file(fileNameIdentifiers, variableType)
{
    def folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
    def fileName = buildFilePath(folderName, 'file_name_descriptors.csv')
//    def fileNameConsole = buildFilePath(folderName, 'console_log.txt')
    mkdirs(folderName)

    File resultsFile = new File(fileName)
//    File consoleFile = new File(fileNameConsole)
    fileNameIdentifiers.eachWithIndex{it, index ->
        resultsFile.append(it+",")
    }
    resultsFile.append(variableType)
    print " ...Project Identifiers File Made"
}

def read_file(projectEntryIdentifiers)
{
    def folderName = buildFilePath(PROJECT_BASE_DIR, 'project_info')
    def fileName = buildFilePath(folderName, 'file_name_descriptors.csv')
    def identifierResults = []
    def outputResults = [:]
    def tissueName = ""
    def tissueIdentifier = ""
    def namedIdentifiers = []
    
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    resultsFile.eachLine {line, index ->
        def nextIndex = line.indexOf(",")
        def nextString = line
            
        while(nextIndex != -1)
        {
            currentString = nextString.substring(0, nextIndex)
            identifierResults += [currentString]
            nextString = nextString.substring(nextIndex+1)
            nextIndex = nextString.indexOf(",")
        
            if(nextIndex == -1)
            {
                identifierResults += [nextString]
            }
        }
    }

    foundResult = false
    identifierResults.eachWithIndex{ it, index ->
        if(it.isEmpty() || foundResult) {foundResult = true; return}
        else {
            outputResults[it] = projectEntryIdentifiers[index]
        }
    }
    
    lastIdentifier = projectEntryIdentifiers[projectEntryIdentifiers.size()-1]
    
    tissue_array.each
    {
        nameIndex = lastIdentifier.indexOf(it)
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
    nameIndex = image_name.toLowerCase().indexOf("20x")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" 20x"}
    nameIndex = image_name.toLowerCase().indexOf("high")
    if(nameIndex != -1)
    {tissueIdentifier = tissueIdentifier+" High Exp"}
    
    outputResults["Tissue"] = tissueName
    outputResults["Tissue_Identifier"] = tissueIdentifier
    
    if(identifierResults.contains("true")) {outputResults["Experimental"] = "TRUE"}
    else {outputResults["Experimental"] = "FALSE"}
    
    def spaceFound = false
    identifierResults.each{
        if(it == "") spaceFound = true
        if(!spaceFound) namedIdentifiers += it
    }
    
    return [outputResults, identifierResults, namedIdentifiers]
}

def find_cells(identifiers) {
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
    def tissueName = identifiers["Tissue"]

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

    return detections
}

def find_signals(PathObject[] nuclei)
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
    
    signalIdentifiers = [
        "rfpMeanMax",
        "rfpMeanMin",
        "rfpMaxMax",
        "rfpMinMin",
        "rfpSTDDevMax",
        "rfpSTDDevMin",
        "gfpMeanMax",
        "gfpMeanMin",
        "gfpMaxMax",
        "gfpMinMin",
        "gfpSTDDevMax",
        "gfpSTDDevMin"
    ]

    return [signalIntensities, signalIdentifiers]
}