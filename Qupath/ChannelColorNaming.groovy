def void defineChannelColors() {
    channelColorsArray = ["Red":"RFP", "Green":"GFP", "Blue":"DAPI"]
//    setChannelColors(-65536, -16776961)
    imageData = getCurrentImageData()
    server = imageData.getServer()
    def channelCount = server.nChannels()
    def int redColor = -65536
    def int greenColor = -16711936
    def int blueColor = -16776961
    def channelOrder = []
    def imageChannel = ""
    def channelColor = ""
    imageName = getProjectEntry().getImageName()
    if(imageName.toLowerCase().contains("confocal")) {
        setChannelColors(imageData, blueColor, greenColor, redColor)//SP8 laser/PMT order
    } else {
        setChannelColors(imageData, redColor, greenColor, blueColor)//DMi8 filter cube order
    }
//    setChannelColors(imageData, redColor, blueColor)//DMi8 filter cube order
    for(i = 0; i < channelCount; i++)
    {
        imageChannel = server.getChannel(i)
        channelColor = imageChannel.getColor()
        
        switch (channelColor) {
            case redColor:
                channelOrder[i] = "Red"
                break
            case greenColor:
                channelOrder[i] = "Green"
                break
            case blueColor:
                channelOrder[i] = "Blue"
                break
            default:
                println "Color not in library"
                break
        }
    }

    channelOrder.eachWithIndex {value,key ->
        switch (key) {
            case 0:
                setChannelNames(imageData, channelColorsArray[channelOrder[key]])
                break
            case 1:
                setChannelNames(imageData, null, channelColorsArray[channelOrder[key]])
                break
            case 2:
                setChannelNames(imageData, null, null, channelColorsArray[channelOrder[key]])
                break
            default:
                break
        }
    }
}

defineChannelColors()