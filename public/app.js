const app = new Vue({
  el: '#app',
  data() {
    return {
      accessKey: "0",
      meta: {appName: "Bot", bot: "botfather", user: ""},
      stickerSet: "",
      items: []
    }
  },
  mounted() {
    let uri = window.location.search.substring(1);
    let params = new URLSearchParams(uri);
    this.accessKey = (params.get("d") || "0")
    fetch('/meta/' + this.accessKey)
      .then(resp => resp.json())
      .then(data => this.meta = data)
  },
  computed: {
    authorized: function() {
      return this.meta.user.length > 0
    }
  },
  methods: {
    onStickerSetChange: function() {
      this.items = []
      fetch('/items/' + this.stickerSet)
        .then(resp => resp.json())
        .then(data => this.items = data)
    },
    save: function(item) {
      fetch('/items', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json;charset=utf-8',
        },
        body: JSON.stringify({
          guid: this.accessKey,
          id: item.id,
          tags: item.tags || ""
        })
      })//.then(resp => alert(resp.status))
    },
    thumbUrl: function(item) {
      return '/thumbs/' + item.stickerSet + '/' + item.id + '.png';
    }
  }
});