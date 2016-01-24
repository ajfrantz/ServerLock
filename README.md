A super basic Android application which tries to wake up a server on my network
using wake-on-LAN, then hold it awake by connecting to a process it runs.

This is more or less the first Android application I ever wrote and represents
just a few brief hours of hacking.  It's probably not a good example to learn
from and likely does almost everything wrong.  (That said, the app *does*
work.)

If you do try to use this code, bear in mind that many things are hardcoded for
my particular network setup (wifi network ssid, server DNS name, local network
IP range).  You'll probably need to fix those first.
