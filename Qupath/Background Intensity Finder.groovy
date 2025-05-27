import qupath.lib.display.ImageDisplay

def imageData = getCurrentImageData()
def server = imageData.getServer()

def imagePixels = new ImageDisplay().create(imageData)
def channelNames = imagePixels.availableChannels()
def RFPHisto = imagePixels.getHistogram(channelNames[0])//SP8 Channel 3 (index 2) is RFP DMi8 Channel 1 (index 0) is RFP

def peaks = findPeaks(RFPHisto)
print(peaks)
def peaksLength = peaks.size()-1
def lastPeak = peaks[peaksLength]
def bgCutoff = lastPeak+(RFPHisto.getStdDev()*1.5)

folderName = buildFilePath(PROJECT_BASE_DIR, 'Analysis Results')
fileName = buildFilePath(folderName, 'Measurements.csv')
imageName = getProjectEntry().getImageName()
makeFile([bgCutoff])

print(imageName+" : "+bgCutoff)

def findPeaks(histogram) {
    def histoBins = histogram.nBins()
    def histoRange = histogram.getEdgeRange()
    def histoBinWidth = histoRange/histoBins
    def appliedVariance = Math.ceil(histoBinWidth)
    def maxPeak = histogram.getMaxCount()
    def variance = histogram.getVariance()
    def stdDev = histogram.getStdDev()
    def maxBin = 0
    def differential = 0
    def biggestDiff = 0
    def peakBin = 0
    def peaks = []
    
    for(int i = 5; i < histoBins; i++) {
        differential = histogram.getCountsForBin(i)-histogram.getCountsForBin(i-1)
        if(biggestDiff > differential) {
            biggestDiff = differential
            peakBin = (histoBinWidth*i)
        }
    }
    peaks.add(peakBin)
    
    def initialBGPeak = (peakBin/histoBinWidth)+10
    differential = 0
    biggestDiff = 0

    for(int i = initialBGPeak; i < histoBins; i++) {
        differential = histogram.getCountsForBin(i)-histogram.getCountsForBin(i-1)
        if(biggestDiff > differential) {
            biggestDiff = differential
            peakBin = (histoBinWidth*i)
        }
    }
    peaks.add(peakBin)

    return peaks
}

def makeFile(data)
{
    mkdirs(folderName)

    File resultsFile = new File(fileName)
    if(!resultsFile.exists()) {
       resultsFile.append("Image Name, Background Threshold\n")
    }
    resultsFile.append(imageName)
    data.each {
       resultsFile.append(","+it)
    }
    resultsFile.append("\n")
}