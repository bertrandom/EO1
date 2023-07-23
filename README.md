# Electric Objects Lives!  Replacement APK

![](e01.png "e01")

## Getting started

### Requirements 

- You need a way to connect a keyboard and mouse to your E01.  I got one of these -- https://www.amazon.com/gp/product/B01C6032G0/ -- and connected my USB keyboard to it, then my USB mouse to the keyboard.
- Flickr API key:  Once you've signed up for [Flickr](https://www.flickr.com), go [here](https://www.flickr.com/services/apps/create/apply/), to create an "app".  Once you walk through the short wizard, your key will look like a series of numbers and letters.
- Flickr User ID:  Your user ID is in the URL bar when viewing your photos.  For example it is bolded in the following URL:  https://www.flickr.com/photos/ **193118297@N04** /
- Upon setting up the app, it'll ask for these two pieces of info.  You can either type them in on the setup dialog, or put them into a file (the User ID, followed by a carriage return, followed by your API key).  Name this file **"config.txt"** and copy this file to your E01's "Downloads" folder.  (An easy way to do this is to email yourself the file then log into your email and download it using the E01's web browser [described below]).

### Setup

- Upload some EO art to your Flickr account.  There's a good collection here:  https://github.com/crushallhumans/eo1-iframe/tree/main/eo1_caches/mp4s -- MP4 videos and still images are supported.
- Once you boot up your E01 and it hangs on the "Getting Art" dialog, hit **WINDOWS + B** to open a web browser
- You need to tell your E01 to allow side-loading.  Swipe down on the top right and go to Settings > Security.  In there make sure "Unknown Sources" is checked.
- Go back to the browser and go to this URL: https://github.com/spalt/EO1/releases/download/0.0.2/EO1.apk
- When it finishes, install the file by pulling down the notification bar and clicking it, then agreeing to the prompts.
- Restart/power cycle your E01
- Because this APK is designated as a "Home screen replacement", when it boots, it will ask if you want to load the Electric Object app, or the E01 app.  Select E01 and choose "Always".
- The first time the E01 is run you will need to specify the information above.  Click OK to save and continue.
- You can now unplug your mouse and keyboard and hang your E01 back on the wall!

### New in 0.0.2

- Fixed: low resolution images
- New: Specify slideshow interval in minutes
- New: Specify showing your gallery, public gallery (items tagged "ElectricObjectLives"), or specific tag
- New: auto-brightness based on light sensor reading
- New: quiet hours (screen will dim and not display images during this period)
- New: Top button will dim/un-dim screen
- New: Space will advance slideshow manually
- New: C opens Config screen
- More goodies coming soon :)

### Art/Contact

- I need more art!  Do you have any?  
- Questions?  danf879@gmail.com