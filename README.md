# "Darunter" Taxi Demo

A demo for Reappt and Bluemix themed on taxis.

The demo models a small town, passengers and taxis. When a passenger appears taxis can bid on the opportunity to give them a ride. The winning taxi collects and delivers the passenger and gets paid the fare.

## Using the demo

You can use the demo through a web application. The application has four tabs to allow you to interact and monitor the different aspects of the demo. The tabs are *World view*, *View auctions*, *Taxis* and *Add passenger*. Every browser using the web application shares the same world however not all state is shared. You will only be able to see an overview of what other people are doing.

### World view

When you open the application you are presented with the *World view*. This gives a graphical overview of the town. You can see the road layout, it's a new town with a grid layout, much like Milton Keynes. There are a handful of  landmarks (transplanted from London). Once added, you will also see passengers and taxis moving around. Passengers will always display some information about themselves. Taxis only display information on the *World view* when you hover the mouse over them.

### Taxis

The *Taxis* tab allows you to add taxis and see detailed information about the taxis your client has added. Where it is, where it is going and what it is doing. It does not show you information about taxis added by other clients.

When a taxi is added it will appear in the list on your *Taxis* page and everyone's *World view* but no one elses *Taxis* list.

Taxi behaviour defaults to roaming around the town looking for fares. When they are not collecting or carrying a passenger they continually pick a random destination and travel there. When they have a fare they first travel to the start of the journey, collect the passenger and then travel to the destination. Once the taxi reaches the destination they start roaming again.

### Passengers and Auctions

The *Add passenger* tab allows you to add new passengers by giving their current location and their intended destination as co-ordinates in the town. The passenger will appear on everyone's *World view* with an bubble saying where they want to go.

Every time a passenger is added a new auction is created. The taxis will bid on auctions by sending the fare they will charge to the backend. The taxi with the lowest fare will win the auction. Every taxi can see the current wining bid and can bid as many times as they want. After a fixed time the backend will close the auction and notify the winning taxi. The winning taxi will travel to the passenger and collect them. Once collected they disappear from the *World view* and the taxi travels to the destination. On reaching the destination the passenger will briefly reappear to announce they have arrived.

The *View auctions* tab shows the state of each auction which is only interesting when both taxis and passengers are present. It shows the passenger, the details of the journey and the current leading bid. The auction starts in an open state, while open new bids are accepted. After 20 seconds the auction changes to an offered state where no new bids are accepted and the leading bidder is notified of their win. Once the taxi acknowledges the win the auction is moved to the accepted state.

Passengers will eventually give up. If you add a passenger without any taxis or enough taxis they will eventually be removed by the backend.

## Implementation

The "Darunter" taxi demo is divided into two parts, a frontend and a backend.

The frontend is a browser based client served as static content. The client has been written in ClojureScript and uses the JavaScript API to speak to the Reappt server.

The backend has been written in Clojure using the Java API to speak to the Reappt server and runs as a process.

Although Clojure and ClojureScript look like the same language because they depend on different Diffusion APIs the code they use to interact with Reappt looks slightly different. For example the Clojure code identifies the target server a URL and the ClojureScript code uses a JavaScript object with the properties `host` and `port`.

## Deploying and hosting

The demo is intended to be deployed as two applications to IBM Bluemix and to connect to a Reappt service also created through Bluemix. Manifest files are provided for both the frontend and backend to allow them to be easily deployed to Bluemix using the CloudFoundry command line tools. It is possible to run locally but the instructions provided are for Bluemix.

### Prerequisites

1. [Git](https://git-scm.com/)
2. [Leiningen](http://leiningen.org/)
3. [Bluemix account](https://console.ng.bluemix.net/)
4. [CloudFoundry CLI tools](https://github.com/cloudfoundry/cli)

### Instructions

1. Set up a space for the application in Bluemix
2. Add a Reappt service to that space
3. Through the Reappt dashboard add the credentials taxi/taxi with the `TOPIC_CONTROL` role
4. Through the Reappt dashboard add the credentials taxi-controller/taxi with the `TOPIC_CONTROL` role
5. Use git to clone the repository
6. Modify the `manifest.yml` of the front- and backend applications to use the correct Reappt service host
7. Modify the `manifest.yml` of the frontend application to use a unique Bluemix routing
8. Use the Leiningen alias `release-build` to build both the front- and backend applications
9. Select the space you added using the CloudFoundry CLI tools
10. Use the CloudFoundry CLI tools to push the front- and backend applications according to the manifest

## Licensing

This project is licenced under Apache License 2.0.

Copyright (C) 2016 Push Technology Ltd.
