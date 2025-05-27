#!/usr/bin/env python
# coding: utf-8

# In[1]:


import pandas as pd
import os


# In[2]:


def getUnits(filePath, valueCheck):
    df = pd.read_csv(filePath)
    return df.head(0).columns[3] == valueCheck


# In[3]:


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
            deltaX = (annotation[1] - anno[1])*0.01
            deltaY = (annotation[2] - anno[2])*0.01
            deltaZ = (annotation[3] - anno[3])*0.01
            deltaLine = ((deltaX**2 + deltaY**2 + deltaZ**2)**0.5)
            # currentMeasurement[currentAnno] = [deltaX, deltaY, deltaZ, deltaLine]
            currentMeasurement[currentAnno] = deltaLine
        points.append(currentAnnotation)
        measurements[currentAnnotation] = currentMeasurement
    print("Measurements have been successfully extracted!")
    return measurements


# In[4]:


def makeExcelFile(dataArray, fileName):
    df = pd.DataFrame.from_dict(measurements, orient='index')
    df.index.name = 'row_names'
    df = df.reset_index()
    df.to_excel(fileName, index=False)
    
    print("Excel file has been successfully created!")


# In[6]:


#Set path to files and gather them into one variable list, based on keyword preference.

path = "Python Checkpoint CSV Files\\"
keyWord = ".csv"
files = [os.path.join(path, file) for file in os.listdir(path) if keyWord.lower() in file.lower()]

df = pd.read_csv(files[0])
firstUnits = df.head(0).columns[3]
# print(files)
for filePath in files:
    measurements = getMeasurements(filePath)
    # print(measurements)
    outputExcelFile = path+"Skull Measurements "+filePath.replace(".csv", "").replace(path, "")+" - Results.xlsx"
    # print(outputExcelFile)
    if (getUnits(filePath, firstUnits)):
        makeExcelFile(measurements, outputExcelFile)
    else:
        print(outputExcelFile+" was not processed due to the measurements being different than the original file in the directory.")

