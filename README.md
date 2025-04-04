(desc generated by AI)
## Steam Screenshot Organizer

A tool that automatically organizes Steam screenshots into game-specific folders.
Native build by graalvm.


## The tool supports two common Steam screenshot storage formats:

### Option 1: Screenshots additional replica
Example:
```
Before:
/screenshotReplica/
  ├── 570_20230615_123456_1.png  (Dota 2 screenshot)
  ├── 730_20230616_234567_1.png   (CS:GO screenshot)
  └── 1097150_20230617_345678_1.png   (Fall Guys screenshot)


After:
/screenshotReplica/
  ├── Dota 2/
  │   └── 570_20230615_123456_1.png 
  ├── Counter-Strike Global Offensive/
  │   └── 730_20230616_234567_1.png 
  └── Fall Guys/
      └── 1097150_20230617_345678_1.png 
```

### Option 2: Normal user Screenshots Directory

Example:
```
Before:
/Steam/
  ├── 570/
  │   └── screenshots/
  │       └── 20230615_123456.jpg
  ├── 730/
  │   └── screenshots/
  │       └── 20230616_234567.jpg
  └── 1097150/
      └── screenshots/
          └── 20230617_345678.jpg

After:
/Steam/
  ├── screenPacks/
  │   ├── Dota 2/
  │   │   └── 20230615_123456.jpg
  │   ├── Counter-Strike Global Offensive/
  │   │   └── 20230616_234567.jpg
  │   └── Fall Guys/
  │       └── 20230617_345678.jpg
  ├── 570/...
  ├── 730/...
  └── 1097150/...
```

## First Run

On the first run, the application will download the Steam app list (approximately 3-4 MB) and save it as `app.json` in the current directory. This file is used to match game IDs with their proper names.

## Notes

- The original directory structure is preserved when using Option 2
- The tool handles invalid filenames and directories gracefully
- If a game name cannot be determined, the app ID is used as the folder name

## Troubleshooting

If you encounter issues with the app list:
1. Delete the `app.json` file
2. Run the application again to download a fresh copy

## License

This project is licensed under the MIT License - see the LICENSE file for details.