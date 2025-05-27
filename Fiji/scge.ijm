//
// Copyright 2020, The Jackson Laboratory, Bar Harbor, Maine
// J. Peterson
//
var Version = "version 1.01";
var ImageTypes = newArray("liver", "kidney", "adrenalgland");
var ImageType = ImageTypes[0];
var Actions = newArray("areas", "counts");
var Action = Actions[0];
var OutputDirectory = "";
var InputImageNoChoice = "< re-populate image list >";
var InputImage = InputImageNoChoice;
var ChannelNames = newArray("RFP", "GFP", "DAPI");

var vEar = newArray("<select ear notch>", "N", "R", "L", "2R", "2L", "3R", "3L");
var vSex = newArray("<select sex>", "M", "F");
var vPortion = newArray("<select portion>", "beginning", "mid", "end");
var vTissue = newArray(
    "<select tissue>",
    "adrenal_gland",
    "bladder",
    "brain",
    "brown_fat",
    "cecum",
    "diaphragm",
    "epididymis",
    "eye",
    "gastro_skeletal_muscle",
    "heart",
    "kidney",
    "large_intestine",
    "liver",
    "lung",
    "lymph_node",
    "ovary",
    "pancreas",
    "skin",
    "small_intestine",
    "spleen",
    "stomach",
    "testis",
    "thymus",
    "uterus");

var sAnimal = "";
var sEar = vEar[0];
var sSex = vSex[0];
var sPortion = vPortion[0];
var sTissue = vTissue[0];
var SampleNumber = 1;

var vLowerThresholds = newArray(10000, 10000, 10000);  // RFP, GFP, DAPI

main();
exit();

function main()
{
    if (!isOpen("Stats"))
    {
        create_empty_table();
    }

    Dialog.createNonBlocking("SCGE.ijm - "+Version);
    mode_list = newArray("interactively process open images", "process lif file");
    Dialog.addChoice("mode", mode_list, mode_list[0]);
    Dialog.show();
    bInteractive = (Dialog.getChoice() == mode_list[0]);

    if (bInteractive)
    {
        process_interactively();
    }
    else
    {
        process_lif_file();
    }
}


//
// Create the empty Measurements table.
//
function create_empty_table()
{
    Table.create("Stats");

    Table.set("Sample", 0, 0);
    Table.set("Animal", 0, " ");
    Table.set("Ear", 0, " ");
    Table.set("Sex", 0, " ");
    Table.set("Portion", 0, 0);
    Table.set("Tissue", 0, 0);
    Table.set("tissue_area", 0, 0);
    Table.set("units", 0, " ");

    Table.set("RFP_area", 0, 0);
    Table.set("GFP_area", 0, 0);
    Table.set("DAPI_area", 0, 0);
    Table.set("RFP_count", 0, 0);
    Table.set("GFP_count", 0, 0);
    Table.set("DAPI_count", 0, 0);

    Table.setLocationAndSize(20, 20, 900, 200) ;
    Table.update();
}


function process_interactively()
{
    while (true)
    {
        process_image();
    }
}


function process_lif_file()
{
    close("*");
    //close("Stats");
    Dialog.createNonBlocking("SCGE.ijm - "+Version);
    Dialog.addFile("input file", "");
    Dialog.addDirectory("Output Directory", OutputDirectory);
    Dialog.addNumber("number of series in lif", 1);
    Dialog.addNumber("RFP lower threshold", vLowerThresholds[0]);
    Dialog.addNumber("GFP lower threshold", vLowerThresholds[1]);
    Dialog.addNumber("DAPI lower threshold", vLowerThresholds[2]);
    Dialog.addCheckbox("use CLAHE", true);
    Dialog.addCheckbox("fast CLAHE", true);
    Dialog.addCheckbox("save validation files", true);

    Dialog.show();

    input_file = Dialog.getString();
    print("LIF: " + input_file);
    OutputDirectory = Dialog.getString();
    OutputDirectory = add_separator(OutputDirectory);
    number_of_series = Dialog.getNumber();
    vLowerThresholds[0] = Dialog.getNumber();
    vLowerThresholds[1] = Dialog.getNumber();
    vLowerThresholds[2] = Dialog.getNumber();
    bUseClahe = Dialog.getCheckbox();
    bUseFastClahe = Dialog.getCheckbox();
    bSaveValidation = Dialog.getCheckbox();

    setBatchMode(true);
    import_cmd = "Bio-Formats Importer";
    for (i=0; i<number_of_series; i++)
    {
        series = "series_" + toString(i+1);
        import_params = "open='" + input_file + "' " + series;
        run(import_cmd, import_params);
        InputImage = getTitle();
        print("InputImage: " + InputImage);
        process_single_image(InputImage, OutputDirectory, bUseClahe, bUseFastClahe, bSaveValidation);
    }
    close("*");
    setBatchMode(false);

    selectWindow("Stats");
    file = OutputDirectory + "Stats.csv";
    saveAs("Results", file);
}


function process_image()
{
    emptyList = newArray(InputImageNoChoice);
    imageList = getList("image.titles");
    imageList = Array.concat(emptyList, imageList);

    Dialog.createNonBlocking("SCGE.ijm - "+Version);

    Dialog.addChoice("Image", imageList, InputImage);
    Dialog.addDirectory("Output Directory", OutputDirectory);
    Dialog.addNumber("RFP lower threshold", vLowerThresholds[0]);
    Dialog.addNumber("GFP lower threshold", vLowerThresholds[1]);
    Dialog.addNumber("DAPI lower threshold", vLowerThresholds[2]);
    Dialog.addCheckbox("use CLAHE", true);
    Dialog.addCheckbox("fast CLAHE", true);
    Dialog.addCheckbox("save validation files", true);
    Dialog.show();

    //
    // close all generated images
    //
    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]);
        close(ChannelNames[i]+"_mask");
        close(ChannelNames[i]+"_validate");
    }
    close("tissue_mask");


    InputImage = Dialog.getChoice();
    if (InputImage == InputImageNoChoice)
    {
        return;
    }
    sAnimal = mouse_from_name(InputImage);
    sEar = ear_from_name(InputImage);
    sSex = sex_from_name(InputImage);
    sPortion = section_from_name(InputImage);
    sTissue = tissue_type_from_name(InputImage);
    OutputDirectory = Dialog.getString();
    OutputDirectory = add_separator(OutputDirectory);
    vLowerThresholds[0] = Dialog.getNumber();
    vLowerThresholds[1] = Dialog.getNumber();
    vLowerThresholds[2] = Dialog.getNumber();
    bUseClahe = Dialog.getCheckbox();
    bUseFastClahe = Dialog.getCheckbox();
    getPixelSize(units, pixelWidth, pixelHeight);
    bSaveValidation = Dialog.getCheckbox();

    split_channels(InputImage);

    setOption("ScaleConversions", false);

    if (bUseClahe)
    {
        for (i=0; i<3; i++)
        {
            selectWindow(ChannelNames[i]);
            sFast = "";
            if (bUseFastClahe)
            {
                sFast = "fast_(less_accurate)";
            }
            run("Enhance Local Contrast (CLAHE)", "blocksize=63 histogram=256 maximum=3 mask=*None* "+sFast);
        }
    }

    vArea = newArray(3);
    for (i=0; i<3; i++)
    {
        a = make_channel_mask(ChannelNames[i], vLowerThresholds[i]);
        a *= (pixelWidth * pixelHeight);
        vArea[i] = round(a);
    }
    a = make_combined_mask();
    a *= (pixelWidth * pixelHeight);
    total_area = round(a);
    vCellCounts = newArray(3);
    for (i=0; i<3; i++)
    {
        vCellCounts[i] = count_spots(ChannelNames[i]);
    }

    selectWindow("Stats");
    row = Table.size();
    if (Table.get("Sample", row-1) == 0)
    {
        row -= 1;
    }
    Table.set("Sample", row, row+1);
    Table.set("Animal", row, sAnimal);
    Table.set("Ear", row, sEar);
    Table.set("Sex", row, sSex);
    Table.set("Portion", row, sPortion);
    Table.set("Tissue", row, sTissue);
    Table.set("tissue_area", row, total_area);
    Table.set("units", row, units);
    Table.set("RFP_area", row, vArea[0]);
    Table.set("GFP_area", row, vArea[1]);
    Table.set("DAPI_area", row, vArea[2]);
    Table.set("RFP_count", row, vCellCounts[0]);
    Table.set("GFP_count", row, vCellCounts[1]);
    Table.set("DAPI_count", row, vCellCounts[2]);
    Table.update();

    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]);
        close(ChannelNames[i] + "_mask");
    }
    close("tissue_mask");
    close("Results");
    run("Tile");

    //
    // Save files
    //
    if (bSaveValidation)
    {
        if (OutputDirectory != File.separator())
        {
            sample_str = add_leading_zeros(row+1, 4);
            dir_name = OutputDirectory + "sample_" + sample_str;
            dir_name = add_separator(dir_name);
            File.makeDirectory(dir_name);
            for (i=0; i<3; i++)
            {
                win = ChannelNames[i] + "_validate";
                selectWindow(win);
                file = dir_name + win + ".tif";
                saveAs("Tiff", file);
                rename(win);
            }
        }
        else
        {
            print("no output directory");
        }
    }
}


function process_single_image(InputImage, OutputDirectory, bUseClahe, bUseFastClahe, bSaveValidation)
{
    //
    // close all generated images
    //
    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]);
        close(ChannelNames[i]+"_mask");
        close(ChannelNames[i]+"_validate");
    }
    close("tissue_mask");

    sAnimal = mouse_from_name(InputImage);
    sEar = ear_from_name(InputImage);
    sSex = sex_from_name(InputImage);
    sPortion = section_from_name(InputImage);
    sTissue = tissue_type_from_name(InputImage);

    getPixelSize(units, pixelWidth, pixelHeight);

    split_channels(InputImage);

    setOption("ScaleConversions", false);

    if (bUseClahe)
    {
        for (i=0; i<3; i++)
        {
            selectWindow(ChannelNames[i]);
            sFast = "";
            if (bUseFastClahe)
            {
                sFast = "fast_(less_accurate)";
            }
            run("Enhance Local Contrast (CLAHE)", "blocksize=63 histogram=256 maximum=3 mask=*None* "+sFast);
        }
    }

    vArea = newArray(3);
    for (i=0; i<3; i++)
    {
        a = make_channel_mask(ChannelNames[i], vLowerThresholds[i]);
        a *= (pixelWidth * pixelHeight);
        vArea[i] = round(a);
    }
    a = make_combined_mask();
    a *= (pixelWidth * pixelHeight);
    total_area = round(a);
    vCellCounts = newArray(3);
    for (i=0; i<3; i++)
    {
        vCellCounts[i] = count_spots(ChannelNames[i]);
    }

    selectWindow("Stats");
    row = Table.size();
    if (Table.get("Sample", row-1) == 0)
    {
        row -= 1;
    }
    Table.set("Sample", row, row+1);
    Table.set("Animal", row, sAnimal);
    Table.set("Ear", row, sEar);
    Table.set("Sex", row, sSex);
    Table.set("Portion", row, sPortion);
    Table.set("Tissue", row, sTissue);
    Table.set("tissue_area", row, total_area);
    Table.set("units", row, units);
    Table.set("RFP_area", row, vArea[0]);
    Table.set("GFP_area", row, vArea[1]);
    Table.set("DAPI_area", row, vArea[2]);
    Table.set("RFP_count", row, vCellCounts[0]);
    Table.set("GFP_count", row, vCellCounts[1]);
    Table.set("DAPI_count", row, vCellCounts[2]);
    Table.update();

    for (i=0; i<3; i++)
    {
        close(ChannelNames[i]);
        close(ChannelNames[i] + "_mask");
    }
    close("tissue_mask");
    close("Results");
    run("Tile");

    //
    // Save files
    //
    if (bSaveValidation)
    {
        if (OutputDirectory != File.separator())
        {
            sample_str = add_leading_zeros(row+1, 4);
            dir_name = OutputDirectory + "sample_" + sample_str;
            dir_name = add_separator(dir_name);
            File.makeDirectory(dir_name);
            for (i=0; i<3; i++)
            {
                win = ChannelNames[i] + "_validate";
                selectWindow(win);
                file = dir_name + win + ".tif";
                saveAs("Tiff", file);
                rename(win);
            }
        }
        else
        {
            print("no output directory");
        }
    }
}



function add_separator(dir)
{
    tmp = dir;
    if (!endsWith(tmp, File.separator()))
    {
        tmp = tmp + File.separator();
    }
    return tmp;
}


function add_leading_zeros(num, length)
{
    num_string = toString(num);
    l = lengthOf(num_string);
    z = "";
    for (i=0; i<length-l; i++)
    {
        z = z + "0";
    }
    s = z + num_string;
    return s;
}


function split_channels(win)
{
    selectWindow(win);
    run("Duplicate...", "title=tmp duplicate");
    run("Split Channels");
    for (i=1; i<=3; i++)
    {
        name = "C" + toString(i) + "-tmp";
        selectWindow(name);
        run("Grays");
        rename(ChannelNames[i-1]);
        
        run("Multiply...", "value=16");
        updateDisplay();
    }
}



function get_max_value(win)
{
    selectWindow(win);

    nBins = 65536;
    row = 0;
    getHistogram(0, counts, nBins);

    getDimensions(width, height, channels, slices, frames);
    totalCount = width * height;
    threshCount = 0.001*totalCount;
    upperBound = nBins-1;
    count = 0;
    while (upperBound > 0)
    {
        count += counts[upperBound];
        if (count > threshCount)
        {
            break;
        }
        upperBound -= 1;
    }
    return(upperBound);
}


function make_channel_mask(win, LowerThreshold)
{
    selectWindow(win);
    mask_name = win + "_mask";
    run("Duplicate...", "title="+mask_name);
    setThreshold(LowerThreshold, 65535);
    setOption("BlackBackground", true);
    run("Convert to Mask");
    run("Close-");  // dilation followed by an erosion
    run("Median...", "radius=1");
    run("Grays");
    getHistogram(0, counts, 2);
    return(counts[1]);
}


function make_combined_mask()
{
    selectWindow("DAPI_mask");
    run("Duplicate...", "title=tissue_mask");
    imageCalculator("OR", "tissue_mask","RFP_mask");
    imageCalculator("OR", "tissue_mask","GFP_mask");
    run("Close-");
    getHistogram(0, counts, 2);
    return(counts[1]);
}

function count_spots(win)
{
    selectWindow(win);
    getPixelSize(unit, pixelWidth, pixelHeight);
    win_ws = win+"_ws";
    run("Duplicate...", "title="+win_ws);
    run("Invert");
    setOption("ScaleConversions", true);
    run("8-bit");
    setAutoThreshold("Otsu");
    run("Convert to Mask");
    run("Watershed");
    run("Set Measurements...", "area centroid redirect=None decimal=2");
    run("Analyze Particles...", "size=5-Infinity display clear");
    A = newArray(nResults);
    for (row=0; row<nResults; row++)
    {
        a = getResult("Area", row);
        A[row] = a;
    }
    Array.sort(A);
    median = A[nResults/2];
    sizeThresh = 2.5 * median;


    count = nResults;
    close(win_ws);
    selectWindow(win);
    name = win+"_validate";
    run("Duplicate...", "title="+name);
    run("RGB Color");
    for (row=0; row<nResults; row++)
    {
        x = getResult("X", row)/pixelWidth;
        y = getResult("Y", row)/pixelWidth;
        a = getResult("Area", row);
        setPixel(x, y, 0xfc21b3);
        extra_count = (a / median) - 1;
        x2 = x + 2;
        while (extra_count > 1.5)
        {
            count++;
            setPixel(x2, y, 0xfc21b3);
            extra_count -= median;
            x2 += 2;
        }
    }
    updateDisplay();
    return(count);
}


function ear_from_name(InputImage)
{
    str = InputImage.toUpperCase();
    i = str.indexOf(" ");
    if (i > 0)
    {
        str = str.substring(i+1);
        for (n=1; n<lengthOf(vEar); n++)
        {
            if (str.startsWith(vEar[n]))
            {
                return vEar[n];
            }
        }
    }

    return "";
}

function section_from_name(InputImage)
{
    str = InputImage.toLowerCase();
    if (str.contains("beginning"))
    {
        return vPortion[1];
    }
    if (str.contains("mid"))
    {
        return vPortion[2];
    }
    if (str.contains("end"))
    {
        return vPortion[3];
    }

    return "";
}


function mouse_from_name(InputImage)
{
    i = InputImage.indexOf(" ");
    if (i > 0)
    {
        str = InputImage.substring(0, i);
        return str;
    }

    return "";

}


function sex_from_name(InputImage)
{
    str = InputImage.toLowerCase();
    if (str.contains("female"))
    {
        return vSex[2];
    }
    if (str.contains("male"))
    {
        return vSex[1];
    }

    i = str.indexOf(" ");
    if (i > 0)
    {
        str = str.substring(i+1);
        i = str.indexOf(" ");
        if (i > 0)
        {
            str = str.substring(i-1);
            if (str.startsWith("f"))
            {
                return vSex[2];
            }
            if (str.startsWith("m"))
            {
                return vSex[1];
            }
        }
    }

    return "";
}


function tissue_type_from_name(InputImage)
{
    str = InputImage.toLowerCase();

    if (str.contains("adrenal") && str.contains("gland"))
    {
        return vTissue[1];
    }
    if (str.contains("bladder"))
    {
        return vTissue[2];
    }
     if (str.contains("brain"))
    {
        return vTissue[3];
    }
    if (str.contains("brown") && str.contains("fat"))
    {
        return vTissue[4];
    }
    if (str.contains("cecum"))
    {
        return vTissue[5];
    }
    if (str.contains("diaphragm"))
    {
        return vTissue[6];
    }
    if (str.contains("epididymis"))
    {
        return vTissue[7];
    }
    if (str.contains("eye"))
    {
        return vTissue[8];
    }
    if (str.contains("gastro") && str.contains("skeletal_muscle") && str.contains("muscle"))
    {
        return vTissue[9];
    }
    if (str.contains("heart"))
    {
        return vTissue[10];
    }
    if (str.contains("kidney"))
    {
        return vTissue[11];
    }
    if (str.contains("large") && str.contains("intestine"))
    {
        return vTissue[12];
    }
    if (str.contains("liver"))
    {
        return vTissue[13];
    }
    if (str.contains("lung"))
    {
        return vTissue[14];
    }
    if (str.contains("lymph"))
    {
        return vTissue[15];
    }
    if (str.contains("ovary"))
    {
        return vTissue[16];
    }
    if (str.contains("pancreas"))
    {
        return vTissue[17];
    }
    if (str.contains("skin"))
    {
        return vTissue[18];
    }
    if (str.contains("intestine") && str.contains("intestine"))
    {
        return vTissue[19];
    }
    if (str.contains("spleen"))
    {
        return vTissue[20];
    }
    if (str.contains("stomach"))
    {
        return vTissue[21];
    }
    if (str.contains("testis"))
    {
        return vTissue[22];
    }
    if (str.contains("thymus"))
    {
        return vTissue[23];
    }
    if (str.contains("uterus"))
    {
        return vTissue[24];
    }

    return "";
}



