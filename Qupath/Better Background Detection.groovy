//Check that next peak/ bg cutoff is after the peak where the tail also goes under the mean
//then add 1.5x the std dev to that value?

import qupath.lib.display.ImageDisplay
//Image data, pixels and intensity values
def imageData = getCurrentImageData()
def server = imageData.getServer()
//Return histogram for red channel
def imagePixels = new ImageDisplay().create(imageData)
def channelNames = imagePixels.availableChannels()
//println(channelNames)
//File output of results
folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results')
fileName = buildFilePath(folderName, 'Measurements.csv')
imageName = getProjectEntry().getImageName()
confocalCheck = false
if(imageName.toLowerCase().contains("confocal")) {
    RFPHisto = imagePixels.getHistogram(channelNames[2])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP
    confocalCheck = true
} else {
    RFPHisto = imagePixels.getHistogram(channelNames[0])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP
}
//Call function to return background
def bg = findBackground(RFPHisto, 2)
print(imageName+" : "+bg)
makeFile(bg)
//Function for finding background based on histogram and number of peaks desired to be used, 2 is standard
def findBackground(histogram, peakCount) {
    //Get values from histogra to be used later
    def histoBins = histogram.nBins()
    def highestSignal = histogram.getEdgeMax()
//    println(highestSignal)
//    println(histoBins)
    def scaleBins = highestSignal / histoBins
//    if(confocalCheck) {
//        scaleBins = 256/highestSignal
//    } else {
//        scaleBins = 4096/highestSignal
//    }
//    println(highestSignal)
//    println(scaleBins)
    def stdDev = histogram.getStdDev()
    def baseLine = histogram.getMeanValue()
//    println(baseLine)
    //Initialize values
    def peaks = []
    def bins = []
    //Iterate x times, where x is the input number above
    for(int j = 0; j < peakCount; j++) {
        //Initialize more values
        currentPeak = 0
        currentBin = 0
        //Iterate over all bins in histogram, except 0 which is true black
        for(int i = 1; i < histoBins-1; i++) {
            //Current intensity value for bin
            currentBinVal = histogram.getCountsForBin(i)
            
            //If the for loop has iterated once, check if the peak it found already is skipped over
            //So there are no duplicates. 1 sd from center is used as the threshold
            
            //Batter way to check would be to use the baseline on eihter side of the peak as it's width
            //This may cause the second peak to be lumped in with the first, as the valley betweeen the 
            //two may not reach the baseline before ocming back up.
            if(bins.size() > 0) {
                for(int k = 0; k < bins.size(); k++) {
                    lowerLim = bins[k-1] - (stdDev*0.5)
                    upperLim = bins[k-1] + (stdDev*0.5)
                    if(i >= lowerLim && i <= upperLim) {
                        i = i + (int)stdDev
                    }
                }
            }
            //If this is the highest peak, assign it's value as such
            if(currentBinVal > currentPeak && currentBinVal !in peaks && currentBinVal > baseLine) {
                currentPeak = currentBinVal
                currentBin = i
            }
        }
        //If peak is highest value possible, most likely the graph shows the bg peaks being very close together
        //Therefore, find the dip, and take the peak after the dip (lowest value between two peaks)
        peaks.add(currentPeak)
        bins.add(currentBin)
//        println("Peak: "+currentPeak+" Bin: "+currentBin)
    }
    
//    print(bins)
    currentValley = histogram.getCountSum()
//    print(currentValley)
    if(bins[1] != 0) {
        for(int i = bins[0]; i <= bins[1]; i++) {
        //        print(i)
            currentBinVal = histogram.getCountsForBin(i)
            if(currentBinVal < currentValley) {
                currentValley = currentBinVal
                currentBin = i
            }
        }
        bgCutoff = currentBin*scaleBins
    }
    else {
        bgCutoff = bins[0]
    }
    //Multiply bin index value by the scaling for n bins to find actual index for threshold
    bins.eachWithIndex {value, key ->
        bins[key] = value*scaleBins
    }
    
    //Check width of second peak by the baseline, and if big, use 2nd option (wide background)
//    if(bins[1] == 0) {
//        bgCutoff = bins[bins.size()-1]+stdDev*2
//    } else {
//        bgCutoff = Math.abs(bins[1]-bins[0])+bins[1]
//    }
    bins.add(bgCutoff)
    
//    print(stdDev)
//    print(peaks)

    return bins
}

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Peaks/ Background Threshold (Last)\n")
    }
    resultsFile.append(imageName)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append("\n")
}

Thread.sleep(100)
// Try to reclaim whatever memory we can, including emptying the tile cache
javafx.application.Platform.runLater {
    getCurrentViewer().getImageRegionStore().cache.clear()
    System.gc()
}
Thread.sleep(100)