#@ ResultsTable rt

import org.apache.commons.io.FileUtils;

def folder = new File("//sv-nas1.rcp.epfl.ch/ptbiop-raw/public")
def files = folder.listFiles();

files.each{
	if( it.isDirectory() ) {
		def sizeMb = FileUtils.sizeOfDirectory( it ) / 1e6
		rt.incrementCounter()
		rt.addValue("Folder", it.getName())
		rt.addValue("Size (MB)", sizeMb)
		
		
		println "${it.getName()} : $sizeMb"
	}
}
rt.show("File size ")