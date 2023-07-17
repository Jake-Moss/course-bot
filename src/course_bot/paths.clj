(ns course-bot.paths
  (:require [slash.command :as cmd]
            [course-bot.handlers :as handler]))


(cmd/defpaths command-paths
  (cmd/group ["sudo"] ; common prefix for all following commands
             (cmd/group ["chart"]
               handler/send-chart
               handler/update-charts)
             (cmd/group ["embed"]
               handler/send-embed
               handler/send-all-embeds
               handler/update-embeds)
             (cmd/group ["set"]
               handler/image-host-channel
               handler/course-regex
               handler/embed-colour
               handler/auto-enroll
               handler/auto-save
               handler/auto-send-embed
               handler/auto-channel-threshold
               handler/additional-roles
               handler/remove-additional-roles
               handler/allow-registration)
             (cmd/group ["course"]
               handler/set-interest
               handler/force-register
               handler/force-deregister
               handler/enroll-all
               handler/unenroll-all
               handler/reset
               handler/dump
               handler/clean)
             handler/create-roles-and-channels
             handler/remove-roles-and-channels
             handler/override
             handler/config
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
