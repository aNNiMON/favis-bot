const app = new Vue({
  el: '#app',
  data() {
    return {
      meta: {appName: "Bot", username: "botfather"},
      stickerSet: "",
      items: []
    }
  },
  mounted() {
    fetch('/meta')
      .then(resp => resp.json())
      .then(data => this.meta = data)
  },
  methods: {
    onStickerSetChange: function() {
      this.items = []
      fetch('/items/' + this.stickerSet)
        .then(resp => resp.json())
        .then(data => this.items = data)
    },
    thumbUrl: function(item) {
      return '/thumbs/' + item.stickerSet + '/' + item.id + '.png';
    }
  }
});