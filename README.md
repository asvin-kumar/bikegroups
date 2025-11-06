# Bike Groups

This is the source code for the [austinbikegroups.com](https://austinbikegroups.com) website.

## Setting up the environment

You will need [babashka](https://babashka.org/) and [node](https://nodejs.org/en) installed. Then running `npm i` will install the dependencies.

## Building the website

```bash
bb download-data
bb build-content
bb build-styles
bb inline-css
```

## Deploying

The site deploys to Cloudflare Pages. You will need access to the `austinbikegroups` Cloudflare Pages project used for this site. Once you have access you can deploy with:

```bash
bb deploy
```

