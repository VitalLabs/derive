#!/bin/bash

command -v npm >/dev/null 2>&1 || {
    echo "nodejs is required but it's not installed."
    echo "See: http://nodejs.org/download/"
    exit 1;
}

command -v bower-installer >/dev/null 2>&1 || {
    echo "bower-installer is required but it's not installed."
    echo "Install with:"
    echo "sudo npm install bower-installer -g"
    exit 1;
}

command -v phantomjs >/dev/null 2>&1 || {
    echo "phantomjs is required but it's not installed."
    echo "Install with:"
    echo "sudo npm install phantomjs -g"
    exit 1;
}

command -v grunt >/dev/null 2>&1 || {
    echo "grunt-cli is required but it's not installed."
    echo "Install with:"
    echo "sudo npm install grunt-cli -g"
    exit 1;
}

npm install
bower-installer

echo -e "\n\nAll deps installed. Now run 'grunt' to start!\n\n"
