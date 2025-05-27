import pandas as pd
import os

def getUnits(filePath, valueCheck):
    df = pd.read_csv(filePath)
    return df.head(0).columns[3] == valueCheck

def getMeasurements(filePath):
    useCols = ['Name', 'X', 'Y', 'Z']
    df = pd.read_csv(filePath, skiprows=1, usecols = useCols)
    #Loop over each annotation and within the loop, do the same to find the coordinate difference between
    # the higher level loop annotation and lower level loop annotation.
    measurements = {}
    points = []
    for annotation in df.values:
        currentAnnotation = annotation[0]
        currentMeasurement = {}
        for anno in df.values:
            currentAnno = anno[0]
            deltaX = annotation[1] - anno[1]
            deltaY = annotation[2] - anno[2]
            deltaZ = annotation[3] - anno[3]
            currentMeasurement[currentAnno] = [deltaX, deltaY, deltaZ]
        points.append(currentAnnotation)
        measurements[currentAnnotation] = currentMeasurement
    print("Measurements have been successfully extracted!")
    return measurements

def makeExcelFile(dataArray, fileName):
    # Extract dynamic headers
    sets = list(dataArray.keys())  # Get all sets (outer keys)
    keys_per_set = list(next(iter(dataArray.values())).keys())  # Get keys inside each set
    num_values = len(next(iter(next(iter(dataArray.values())).values())))  # Number of values per key
    
    # Generate MultiIndex column headers dynamically
    column_tuples = [("Set", "")]
    for key in keys_per_set:
        column_tuples.append((key, "X"))
        column_tuples.append((key, "Y"))
        column_tuples.append((key, "Z"))
    
    # Process data into rows
    rows = []
    for set_name, keys in dataArray.items():
        row = [set_name]
        for key in keys_per_set:
            row.extend(keys[key])  # Extract values dynamically
        rows.append(row)
    
    # Create DataFrame
    df = pd.DataFrame(rows, columns=pd.MultiIndex.from_tuples(column_tuples))
    
    # Save to Excel
    df.to_excel(fileName, index=True)
    
    print("Excel file has been successfully created!")

path = "Python Checkpoint CSV Files\\"
keyWord = ".csv"
files = [os.path.join(path, file) for file in os.listdir(path) if keyWord.lower() in file.lower()]
firstFilePath = files[0]
df = pd.read_csv(firstFilePath)
firstUnits = df.head(0).columns[3]
for filePath in files:
    measurements = getMeasurements(filePath)
    # print(measurements)
    outputExcelFile = path+"Skull Measurements "+filePath.replace(".csv", "").replace(path, "")+" - Results.xlsx"
    # print(outputExcelFile)
    if (getUnits(filePath, firstUnits)):
        makeExcelFile(measurements, outputExcelFile)
    else:
        print(outputExcelFile+" was not processed due to the measurements being different than the original file in the directory.")