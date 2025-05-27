// Get dependencies if needed for RESTFUL API and for LDAP Connection
@Grab(group = "com.konghq", module = "unirest-java", version = "3.13.6", classifier = 'standalone')
@Grab( group="org.apache.directory.api", module="api-all", version="2.1.0" )

#@ String email (label="User Email")
#@ String projectName (label="New Project Name (can contain spaces)")
#@ Boolean dryRun (label="Dry Run, do not create anything")
#@ String overwriteDate (label="overwrite date, format YYYMMdd", value="")


// Run the magic
def userman = new BIOPUserManager(email: email, 
								  projectName: projectName,
								  projectDate: overwriteDate,
								  dryRun: dryRun )


userman.parseUserInformation()
userman.createProjectFolder()
userman.createTrelloCard()
userman.createReadMe()
userman.createEmailToUser()

return

// The class that does it all
class BIOPUserManager {
	
	// Date format for the folder
	def static dateFormat = "YYYYMMdd"
	
	// BIOP Server Location of User Folder
	def biopServerRoot = new File( "\\\\sv-nas1.rcp.epfl.ch\\ptbiop-raw\\public\\" )
	
	
	// Trello API entry point
	def static trelloAPIURL = "https://api.trello.com"
	
	// The Trello List ID to which we add the cards (ONGOING)
	def trelloListID ="5be04ee546786411550c5460" 
	
	// The Default Trello Card that has the default checklist
	def cardSourceID = "630c96e82e793e01f648a218"
	
	// IPA Information from Trello
	def ipaFullName
	def ipaUserName
	def ipaID
	
	// Keys for Trello (This is Oli's, but whatever)
	def static key
	def static token
	
	// Read the Trello key and token from a credentials file made by the user
	static {	
		def userHomeDir = System.getProperty("user.home");

		def creds = new File ( userHomeDir +"/.trello/" )

		def credFile = new File( creds, "credentials" )

		def lines = credFile.readLines()
		
		this.key = lines[0]
		this.token = lines[1]
	}

	// Information to be defined
	
	// User email
	def email
	
	// Project name
	def projectName 

	// This will store the user's group after an LDAP search
	def group

	// A project name with _ instead of spaces and the day appended
	def finalProjectName
	
	// The location of the user folder
	def userFolder

	// The location of the project folder that will be created
	def projectFolder
	
	// The project date, formatted by `dateFormat`
	def projectDate
	
	// The trello card name 
	def cardName
	
	// The URL to the Trello card
	def trelloURL
	
	// Whether to run all steps or not
	def dryRun

	
	// Need to get the Group name and parse the email to create the folder name for trello and the data
	def parseUserInformation() {
		
		// Clean Project name to make sure it has no spaces
		this.finalProjectName = projectName.replaceAll( " ",  "_" )
		
		// Get the email before the @
		def user = email.split("@")[0]
		
		// Get the unit (e.g. UPLUT)
		group = getGroup( email )
		
		if (this.projectDate == "" ) {
			// Get a timestamp
			def date = new Date()
			
			this.projectDate = date.format( dateFormat )
		}
		
		this.userFolder = new File( biopServerRoot, user + "_" + group )
		IJ.log( "User folder ----- '" + this.userFolder.getName() + "' will be made in '" + this.userFolder.getParent() + "'" )
		
		this.projectFolder = new File( userFolder, this.finalProjectName + "_" + projectDate  )
		IJ.log( "New user project ----- '" + this.projectFolder.getName() + "' will be made in '" + this.userFolder.getName() + "'" )
	}
	
	def createProjectFolder() {
		if( dryRun ) {
			IJ.log( "Not creating user Folder. Dry Run." )
			return 
		}
		
		// Create the project folder
		this.projectFolder.mkdirs()
			
		
		// Make Code, Data and Results Folders
		def codeFolder = new File ( this.projectFolder, "Code" )
		codeFolder.mkdirs()
		
		def dataFolder = new File ( this.projectFolder, "Data" )
		dataFolder.mkdirs()
		
		def resultsFolder = new File ( this.projectFolder, "Results" )
		resultsFolder.mkdirs()
		
		// Open the folder in Windows
		new ProcessBuilder("explorer.exe", projectFolder.getAbsolutePath()).start();
		
		return this.projectFolder
	}
	
	// LDAP Query to get the default group from a user
	def getGroup( def email ) { 
		
		// Connect to EPFL LDAP
		def  connection = new LdapNetworkConnection( 'ldap.epfl.ch' );
		
		// Authenticate (Bind) anonymously
		connection.bind()
		
		// Build a searchRequest that will look for the group name of the first group this email is associated with
		def  req = new SearchRequestImpl()
		
		// Search everywhere
		req.setScope( SearchScope.SUBTREE )
		// 'ou' is the group the person belongs to
		req.addAttributes( 'ou' )
		
		req.setTimeLimit( 0 )
		
		// We expect a single result anyway but let's limit it
		req.setSizeLimit( 1 )
		
		// This is the start location for the search: all of EPFL
		req.setBase( new Dn( 'o=epfl,c=ch' ) )
		
		// Here we are looking for a person AND an email that should exactly match AND the default group of the person
		req.setFilter( "(&(objectClass=person)(mail=${email})(EPFLAccredOrder=1))" )
		    
		// Finally run the search
		def results = connection.search( req )
		
		// Pick the results as a list, otherwise it is an iterator and we cannot ask its size without it being clijx.resetMetaData(null)
		// Probably a bug, but considering it is a small list, I am not worried too much about it
		def resList = results.toList()
		
		def group = 'UNKNOWN'
		
		//IJ.log( ""+resList)
		
		if( resList.size() >= 1)
			group =  resList.get( 0 ).getEntry().get( 'ou' )[0]
		
		// Close the connection to the results
		results.close()
		
		// End the connection to LDAP
		connection.close()
		return group
	}

	// Use Trello API to create a Trello card
	def createTrelloCard() {
		
		// Build the card name as "userfolder\project_date"
		
		def cardName = this.userFolder.getName() + "\\" + this.projectFolder.getName()
		
		IJ.log( "Creating Trello Card '${cardName}'..." )
		
		
		// http://unirest.io/java.html
				
		// Get member whose tokens these are
		def member = Unirest.get( trelloAPIURL+"/1/members/me" )
		.header( "Accept", "application/json" )
		.queryString( "key", key )
		.queryString( "token", token )
		.asJson()
		
		this.ipaUserName = member.getBody().getObject().getString( "username" )
		this.ipaFullName = member.getBody().getObject().getString( "fullName" )
		this.ipaID = member.getBody().getObject().getString( "id" )
		
		if ( dryRun ) {
			IJ.log( "Skipping Trello Card Creation with name: "+cardName )
			return
		}
		
		// Create card https://developer.atlassian.com/cloud/trello/rest/api-group-cards/
		def response = Unirest.post( trelloAPIURL+"/1/cards" )
		.header( "Accept", "application/json" )
		.queryString( "idList", this.trelloListID )
		.queryString( "key", this.key )
		.queryString( "token", this.token )
		.queryString( "name", cardName )
		.queryString( "idMembers", this.ipaID )
		.queryString( "idCardSource", this.cardSourceID )
		.queryString( "keepFromSource", "checklists" )
		.queryString( "desc", "[Local Data Location on SVRAW1](file://${this.projectFolder})\n\nAutomatic Desciption, Please Fill" )
		.asJson()
		
		def id = response.getBody().getObject().getString( "id" )
			
		//Unirest.shutdown()	
		this.trelloURL =  "https://trello.com/c/"+id+"/"
		
		IJ.log( "Trello Card created at " + trelloURL )
		BrowserLauncher.openURL( trelloURL )
		
		// Add the trello as a link under the ReadMe.md
		createInternetShortcut("Trello Card", this.projectFolder, trelloURL) 
		
		return trelloURL
	}
	
	// Build a simple readme file to get things startes
	def createReadMe() {
		
		if( dryRun ) {
			IJ.log( "Not creating Readme.md file. Dry Run." )
			return 
		}
		
		def readme = new File( this.projectFolder, "readme.md")
    	readme.write "# ${this.projectName}\n"
    	readme << "Created by: ${ipaFullName} on ${this.projectDate}\n"
    	
    	if( this.trelloURL != null ) {
    		readme << "Trello card: ${this.trelloURL}\n"
    	}
	}
	
	// Create an email template that can be sent to the user after
	def createEmailToUser() {
		Desktop desktop;
		
		// Prepare project location for PC and mac
		def projectMac = this.projectFolder.getAbsolutePath().replaceAll("\\\\\\\\", "smb://").replaceAll("\\\\", "/")
		def projectWin = this.projectFolder.getAbsolutePath()
		
		// Use Desktop from Java to call the default mail client
		if ( Desktop.isDesktopSupported() && (desktop = Desktop.getDesktop()).isSupported(Desktop.Action.MAIL)) {
			
			def contents = "Project '${this.projectName}', \n" +
			"Created on ${this.projectDate}\n\n" +
			"Server Location (WIN): $projectWin\n" +
			"Server Location (MAC): $projectMac\n" +
			"\n BIOP INTERNAL USE: Trello Card: "+this.trelloURL
  			
  			// Need to build the URI for creating the email
  			// Normally URIBuilder would directly return a proper URI but
  			// Spaces " " get transformed to "+" which is incompatible with 'mailto'
  			// So we need to convert "+" to "%20", hence the ugly thing of making 2 URIs
  			def mailto = new URI(new URIBuilder().setScheme("mailto")
  							.setHost(this.email)
  							.addParameter("subject", this.projectName)
  							.addParameter("body", contents).build().toString().replace("+", "%20"))
  			
  			if( dryRun ) {
				IJ.log( "Not sending email. Dry Run." )
			return 
			}				
  			// This launches the email application
  			desktop.mail(mailto);
  			
		} else {
			IJ.log("Cannot Open Mail application");
		}
	}
	
	// Create a URL icon
	def createInternetShortcut(String name, File where, String target) {
		File file = new File(where, name +".url")
	    file.write "[InternetShortcut]\n"
	    file << "URL=" + target + "\n"
	}
}

// Get a simple logger
import ij.IJ

// REST API for Trello
import kong.unirest.Unirest

// BrowserLauncher for opening the web browser at the new Trello card URL
import ij.plugin.BrowserLauncher

// Connecting to EPFL LDAP Server
import org.apache.directory.ldap.client.api.*
import org.apache.directory.api.ldap.model.message.*
import org.apache.directory.api.ldap.model.name.Dn

// To call mail client
import java.awt.Desktop

// For formatting URIs in the email properly
import org.apache.http.client.utils.URIBuilder