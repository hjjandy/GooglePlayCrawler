# GooglePlayCrawler
An implementation for crawling google play apps based on an existing googleplaycrawler (https://github.com/Akdeniz/google-play-crawler).

Download apps into apk files and corresponding description into html files. All downloaded apps are recorded in a sqlite3 database.

By default, the database and the folder for apps are created at the first time running the crawler.

To run the crawler, use command:
   crawl.(sh/bat) -H DEST_HOME [OPTIONS]

Here DEST_HOME points to a directory storing downloaded apps and the database (by default).

[OPTIONS] can be one of the following:

  -*l*                : Crawler will list the app categories can download possible *free* apps. On smartphones, we can list quite a lot of apps for each subcategory (e.g. Free in GAME) but this crawler only lists at most 100 apps.

  -*k KEYWORD_FILE*   : Given a keyword file, in which one keyword (or phrase) each line. Crawler searches the keywords and download free apps. At most 250 apps can be obtained for each keyword.
  
Other options can be known by executing the crawler.

In order to use the crawler, a valid account/password is requried. And the user should provide a device id. If no device id, you can use the "checkin" command in original googleplaycrawler to generate a devide id.
These information are put into dat/crawler.conf (by default in crawler.sh; can be a different file using "--conf").

At most 100 apps are downloaded each time. Then the crawler sleeps for 5 minutes and continues.

Raccoon (https://github.com/onyxbits/Raccoon) slightly modifies the original crawler to support newer version (e.g. sdk_version is 16) in original crawler.
