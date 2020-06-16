const app = new Vue({
  el: '#app',
  data: {
    meta: {appName: "Bot", username: "botfather"},
    stickerSet: "",
    items: []
  },
  created: function() {
    this.init()
  },
  methods: {
    init: function(event) {
      fetch('/meta')
        .then(resp => resp.json())
        .then(data => this.meta = data);
    },
    allItems: function(event) {
      fetch('/items/')
        .then(resp => resp.json())
        .then(data => this.items = data);
    },
    thumbUrl: function(item) {
      return '/thumbs/' + item.stickerSet + '/' + item.id + '.png';
    }
  }
});