(ns course-bot.paths
  (:require [slash.command :as cmd]
            [course-bot.handlers :as handler]))


(cmd/defpaths command-paths
  (cmd/group ["sudo"] ; common prefix for all following commands
             handler/reset
             handler/clean
             handler/set-interest
             handler/create-roles-and-channels
             handler/remove-roles-and-channels
             handler/additional-roles
             handler/remove-additional-roles
             handler/dump
             handler/chart
             handler/update-charts
             handler/auto-enroll
             handler/auto-save
             handler/force-register
             handler/force-deregister
             handler/enroll-all
             handler/unenroll-all
             handler/override
             handler/course-regex
             handler/config
             handler/image-host-channel
             handler/embed-colour
             handler/save
             handler/ping
             handler/unknown)

  (cmd/group ["fun"] ; common prefix for all following commands
             handler/reverse-input
             handler/mock
             handler/unknown)

  (cmd/group ["course"] ; common prefix for all following commands
             handler/register
             handler/deregister
             handler/unknown))

(cmd/defpaths autocomplete-paths
  (cmd/group ["course"]
    handler/register-autocomplete
    handler/deregister-autocomplete))
