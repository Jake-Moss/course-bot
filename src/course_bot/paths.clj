(ns course-bot.paths
  (:require [slash.command :as cmd]
            [course-bot.handlers :as handler]))


(cmd/defpaths command-paths
  (cmd/group ["sudo"] ; common prefix for all following commands
             handler/reset
             handler/clean
             handler/set-interest
             handler/dump
             handler/chart
             handler/ping
             handler/unknown)

  (cmd/group ["fun"] ; common prefix for all following commands
             handler/reverse-input
             handler/mock
             handler/unknown)

  (cmd/group ["course"] ; common prefix for all following commands
             handler/course
             handler/unknown))

(cmd/defpaths autocomplete-paths
  (cmd/group ["course"]
    handler/course-autocomplete))
