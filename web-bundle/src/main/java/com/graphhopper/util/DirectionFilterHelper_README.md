# DirectionFilterHelper Data Flow

```mermaid
flowchart TD
    Start([filterPathsByDirection]) --> CheckNull{lineStrings null<br/>or empty<br/>or size == 1?}
    CheckNull -->|Yes| ReturnInput[Return lineStrings<br/>or empty list]
    CheckNull -->|No| CheckDirection{direction ==<br/>BOTH or UNKNOWN?}
    
    CheckDirection -->|Yes| ReturnAll[Return all lineStrings]
    CheckDirection -->|No| GetPoints[Get terminal points:<br/>- furthestPointOfFirstPath<br/>- furthestPointOfSecondPath]
    
    GetPoints --> CalcSeparation[Calculate total separation<br/>and half separation<br/>using Pythagorean theorem]
    
    CalcSeparation --> InitSelect[Initialize:<br/>selectFirstPath = true<br/>isNonCardinal = check direction]
    
    InitSelect --> SwitchDirection{Switch on<br/>direction type}
    
    SwitchDirection -->|NORTH or SOUTH| CheckYDiff{Y difference <<br/>half separation?}
    CheckYDiff -->|Yes| CheckNonCardinal
    CheckYDiff -->|No| CompareY[compareCoordinates on Y<br/>Update selectFirstPath]
    CompareY --> CheckNonCardinal
    
    SwitchDirection -->|EAST or WEST| CheckXDiff{X difference <<br/>half separation?}
    CheckXDiff -->|Yes| CheckNonCardinal
    CheckXDiff -->|No| CompareX[compareCoordinates on X<br/>Update selectFirstPath]
    CompareX --> CheckNonCardinal
    
    SwitchDirection -->|Other| CheckNonCardinal{Is non-cardinal<br/>direction?}
    
    CheckNonCardinal -->|No| ReturnSelected
    CheckNonCardinal -->|Yes| CalcBearing[Calculate bearing<br/>using AngleCalc.calcAzimuth]
    
    CalcBearing --> DetermineTarget[Determine target bearing<br/>based on direction<br/>and buildUpstream]
    
    DetermineTarget --> CalcDiff[Calculate angular difference<br/>normalize to 0-180°]
    
    CalcDiff --> CheckBearing{Difference > 120°?}
    CheckBearing -->|Yes| SetFalse[selectFirstPath = false]
    CheckBearing -->|No| KeepTrue[selectFirstPath stays true]
    
    SetFalse --> ReturnSelected[Return selected path<br/>based on selectFirstPath]
    KeepTrue --> ReturnSelected
    
    ReturnSelected --> End([End])
    ReturnInput --> End
    ReturnAll --> End
    
    style Start fill:#e1f5ff
    style End fill:#e1f5ff
    style ReturnSelected fill:#c8e6c9
    style ReturnInput fill:#c8e6c9
    style ReturnAll fill:#c8e6c9
    style CheckNull fill:#fff9c4
    style CheckDirection fill:#fff9c4
    style CheckNonCardinal fill:#fff9c4
    style CheckYDiff fill:#fff9c4
    style CheckXDiff fill:#fff9c4
    style CheckBearing fill:#fff9c4
    style SwitchDirection fill:#ffe0b2
```

## Flow Description

The diagram illustrates the data flow through the `DirectionFilterHelper.filterPathsByDirection()` method:

1. **Entry point**: `filterPathsByDirection` method receives lineStrings, buildUpstream flag, and direction
2. **Early exits**: Returns immediately for null/empty lists or BOTH/UNKNOWN directions
3. **Cardinal direction handling**: Uses coordinate comparison for N/S/E/W with separation checks
4. **Non-cardinal direction handling**: Uses bearing calculations for NE/SE/SW/NW
5. **Decision logic**: The `selectFirstPath` boolean determines which path is returned
6. **Helper methods**: `getTerminalPoint` and `compareCoordinates` are invoked within the flow

## Pythagorean Theorem in Distance Calculation

The algorithm uses the Pythagorean theorem to calculate the total separation between the terminal points of two paths:

```
                    Point 2 (x₂, y₂)
                         ●
                        /|
                       / |
                      /  |
        totalSep     /   | yDiff = |y₂ - y₁|
                    /    |
                   /     |
                  /      |
                 /       |
                /        |
               ●---------+
        Point 1 (x₁, y₁)
               
               xDiff = |x₂ - x₁|


    totalSeparation = √(xDiff² + yDiff²)
    halfSeparation = totalSeparation / 2
```

**Key Concepts:**
- **Point 1**: Terminal point of first LineString (`furthestPointOfFirstPath`)
- **Point 2**: Terminal point of last LineString (`furthestPointOfSecondPath`)
- **xDiff**: Horizontal distance between points = `|x₂ - x₁|`
- **yDiff**: Vertical distance between points = `|y₂ - y₁|`
- **totalSeparation**: Euclidean distance using `√((x₂ - x₁)² + (y₂ - y₁)²)`
- **halfSeparation**: Used as threshold to determine if paths are divergent enough for directional filtering

**Example Use Case:**
For NORTH/SOUTH filtering, if `yDiff < halfSeparation`, the paths are too horizontal (not divergent enough in the Y direction) to make a reliable directional determination, so the algorithm defaults to returning the first path.
