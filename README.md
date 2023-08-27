# Electric Objects Lives!  Replacement APK

![](e01.png "e01")

This is fork of [splat's EO1 Android app](https://github.com/spalt/EO1) that used Flickr as a backing datastore. This uses an nginx directory listing as a datastore, which can either be on your local network or on the public Internet.

If you used splat's app and want to try this one, you'll need to uninstall that app because this will be signed differently.

## Getting started

Some terminology:

| Playlists URL      |   This is the base URL where your playlists are stored in different directories.    |
| Playlist      |    This is the name of the playlist   |

If you look at:
http://eo.twonodes.com/

you can see the basic structure.

`http://eo.twonodes.com` would be the Playlist URL and `community` would be an example of a playlist.

### Requirements 

- You need a way to connect a keyboard and mouse to your EO1.  I got one of these -- https://www.amazon.com/gp/product/B01C6032G0/ -- and connected my USB keyboard to it, then my USB mouse to the keyboard.
- A place to host your images and video with your web server configured to do directory listing.
- Upon setting up the app, it'll ask for these two pieces of info.  You can either type them in on the setup dialog, or put them into a file (the playlists URL, followed by a carriage return, followed by a playlist name).  Name this file **"config.txt"** and copy it to your EO1's "Downloads" folder.  (An easy way to do this is to email yourself the file then log into your email and download it using the EO1's web browser [described below]).

### Setup

Create a directory to hold your art. Each subdirectory will be a playlist, inside those subdirectories you can put your images and video. Video must end in `.mp4` to be recognized as video, any other file will be treated as an image.

You'll need to run a webserver to host your art - most webservers with directory listing should work, but I've only tested with nginx. Let me know if there's a webserver directory listing that appears to be incompatible.

Here's an nginx config that I used:
```
server {
    listen 80;
    server_name eo.twonodes.com;
    root /web/eo.twonodes.com;
    index index.html;
    location / {
	autoindex on;
        try_files $uri $uri/ =404;
    }
}
```

Note that the EO1 runs Android 4.4, which [does not support modern Lets Encrypt certificates](https://community.letsencrypt.org/t/several-sites-unreachable-with-android-4-4-since-chain-changes/165795). There may be a way around this by installing the active ISRG Root X1 certificate from [here](https://letsencrypt.org/certificates/), but at least on my device this would require adding a required PIN code to the device when it sleeps, which wouldn't work. If you figure out how to do this, let me know!

This means that if you use Lets Encrypt for your https server, calls to your server will likely fail. I decided to host my art insecurely rather than deal with this.

- Once you boot up your EO1 and it hangs on the "Getting Art" dialog, hit **WINDOWS + B** to open a web browser
- You need to tell your EO1 to allow side-loading.  Swipe down on the top right and go to Settings > Security.  In there make sure "Unknown Sources" is checked.
- Go back to the browser and go to this URL: http://eo.twonodes.com/EO1.apk
- When it finishes, install the file by pulling down the notification bar and clicking it, then agreeing to the prompts.
- Restart/power cycle your EO1
- Because this APK is designated as a "Home screen replacement", when it boots, it will ask if you want to load the Electric Object app, or the EO1 app.  Select EO1 and choose "Always".
- The first time the EO1 is run you will need to specify the information above.  Click OK to save and continue.  **To get back to the configuration screen later, push C on your connected keyboard** 

Example configuration:

Playlists URL: http://eo.twonodes.com
Playlist: community

- You can now unplug your mouse and keyboard and hang your EO1 back on the wall!

### Controlling the EO1

This app comes with a partner app, but I don't actually have an Android device to run the partner app. This doesn't mean you can't use the partner functionality - it uses sockets to communicate. This means that you can use `nc` from the CLI (or integrate this into your own client app):

Examples:
```
echo "playlist,computing" | nc 192.168.1.205 12345
echo "resume" | nc 192.168.1.205 12345
echo "video,http://eo.twonodes.com/commissioned/Pi-Slices_Range.mp4" | nc 192.168.1.205 12345
echo "image,http://eo.twonodes.com/community/14738453_3205017_lz.jpg" | nc 192.168.1.205 12345
echo "options,1.0,300,23,7" | nc 192.168.1.205 12345
```

If you use image or video to load a single URL, your playlist will be paused. You should send a resume command to continue the playlist again.

options parameters: brightness, interval (in seconds), sleep hour, wake hour

`192.168.1.205` should be replaced by the IP address of your EO1, which you should see as a toast when it's starting a playlist.

### New in 0.0.6

Use nginx directory listing instead of Flickr as a backing store
Add a "playlist" socket command

### New in 0.0.5

Device app:
- Checkbox allows you to disable auto-brightness and set it manually

Partner app:
- Gear icon at top right lets you send new values for brightness, start/end quiet hour, and slideshow interval direct to the device (see 0.0.3 for instructions on installing the partner app)

### New in 0.0.4

Device app:
- Fixes quiet hours after midnight
- Fixes image and video pushing via partner app when original resolution is not available
- Better handling of bad/no network scenarios

Partner app:
- Handles intents from Flickr app in a better way (you must upgrade the partner app if you update to 0.0.4 on the device)

### New in 0.0.3

- New: "Partner App" (for Android) runs on your phone or mobile device and allows you to push images or video directly from the <A href="https://play.google.com/store/apps/details?id=com.flickr.android&hl=en_US&gl=US">Flickr Android App</a> using the share icon, assuming you are running on the same network as your EO1 device.  Running the Partner App from the Start menu of your phone allows you to skip to the next item in the current slideshow or resume the slideshow after sharing an individual item.  You can also update the current Tag (original Tag will be restored next time the device restarts).  You must allow Unknown Sources to install this app and point your phones web browser to https://github.com/spalt/EO1/releases/download/0.0.5/EO1-Partner.apk (I may publish this to the Play store in the near furture).
- Updated: Images will be displayed using the "Center Inside" cropping strategy
- Updated: Changed Flickr API to use the highest resolution image (if allowed) and video.

### New in 0.0.2

- Fixed: low resolution images
- New: Specify slideshow interval in minutes
- New: Specify showing your gallery, public gallery (items tagged "ElectricObjectsLives"), or specific tag
- New: auto-brightness based on light sensor reading
- New: quiet hours (screen will dim and not display images during this period)
- New: Top button will dim/un-dim screen
- New: Space will advance slideshow manually
- New: C opens Config screen
- More goodies coming soon :)

### Art/Contact

- I need more art!  Do you have any?  
- Questions?  danf879@gmail.com
