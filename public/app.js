const app = new Vue({
  el: '#app',
  data() {
    return {
      meta: {appName: "Bot", bot: "botfather", user: ""},
      stickerSet: "",
      items: []
    }
  },
  mounted() {
    let uri = window.location.search.substring(1); 
    let params = new URLSearchParams(uri);
    fetch('/meta/' + (params.get("d") || "0"))
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