selectWindow("open");
run("Duplicate...", "title=open-copy");
selectWindow("open-copy");
for(prob = 0.5; prob > 0; prob = prob-0.05)
{
	run("Command From Macro", "command=[de.csbdresden.stardist.StarDist2D], args=['input':'open-copy', 'modelChoice':'Versatile (fluorescent nuclei)', 'normalizeInput':'true', 'percentileBottom':'1.0', 'percentileTop':'99.8', 'probThresh':'"+prob+"', 'nmsThresh':'0.4', 'outputType':'Both', 'nTiles':'1', 'excludeBoundary':'2', 'roiPosition':'Automatic', 'verbose':'false', 'showCsbdeepProgress':'false', 'showProbAndDist':'false'], process=[false]");
	selectWindow("Label Image");
	win = "Lymph_"+prob+"_Star";
	rename(win);
	saveAs("Tiff", "C:/Users/savile/Desktop/"+win+".tif");
	close(win);
}
close("open-copy");